package org.eclipse.cdt.buildconfig.txt;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * This class provides common functionality for operating on an
 * IPath based tree (stored in a sorted Map).
 *
 * In particular propogate upwards pushes common attributes
 * up the tree
 */
public class PathTreeUtils {
	static boolean DEBUG_PATH_TREE = false;

	/**
	 * This class acts as a placeholder in the tree to demarkate
	 * each depth as we aggregate up the tree
	 */
	private final static class FixedDepthPathIndex extends Path {
		public final int depth;
		public FixedDepthPathIndex(int depth) {
			super("");
			this.depth = depth;
		}
		@Override
		public int segmentCount() {
			return depth;
		}
	}

	/**
	 * Sort the paths naturally (i.e. by parent...) segments
	 * where string comparison is equal, shortest paths first.
	 */
	public static class NaturalPathSorter implements Comparator<IPath> {
		public int compare(IPath o1, IPath o2) {
			for (int i = 0; i < o1.segmentCount(); i++) {
				if (i == o2.segmentCount())
					return 1;
				int comp = o1.segment(i).compareTo(o2.segment(i));
				if (comp != 0)
					return comp;
			}
			if (o2.segmentCount() > o1.segmentCount())
				return -1;
			return 0;
		}
	}

	/**
	 * Comparator which sorts paths in ascending order starting from the root...
	 * Inverse of DeepestFirstPathComparator
	 */
	public static class ParentFirstPathComparator extends DeepestFirstPathComparator {
		@Override
		public int compare(IPath o1, IPath o2) {
			return -super.compare(o1, o2);
		}
	}
	/**
	 * This comparator sorts paths by depth such that all deepest paths
	 *  occur before shallow paths
	 * For paths of the same length, sorting is done
	 *     /other/path/to/...
	 *     /path/to/folder/file
	 *     /path/to/file1
	 *	   /path/to/file2
	 *	   /path/to
	 */
	public static class DeepestFirstPathComparator implements Comparator<IPath> {
		public int compare(IPath o1, IPath o2) {
			if (o1.segmentCount() < o2.segmentCount())
				return +1;
			else if (o1.segmentCount() > o2.segmentCount())
				return -1;
			//Path separators always come first
			if (o1 instanceof FixedDepthPathIndex &&
					!(o2 instanceof FixedDepthPathIndex))
				return -1;
			else if (o2 instanceof FixedDepthPathIndex &&
					!(o1 instanceof FixedDepthPathIndex))
				return 1;

			String[] s1 = o1.segments();
			String[] s2 = o2.segments();

			for (int i = 0; i < s1.length; ++i) {
				int comparison = s1[i].compareTo(s2[i]);
				if (comparison != 0)
					return comparison;
			}
			return 0;
		}
	}

	/**
	 * This method's job is to push commonality between objects of type T at a given depth
	 * in the tree, up the tree.  i.e. it pushes "attribute" items where T.equal(otherT) up the tree.
	 *
	 * The passed in map should be sorted using this class's DeepestFirstPathComparator
	 *
	 * It operates in place on the passed in TreeMap.
	 *
	 * @param TreeMap IPath -> LinkedHashSet containing objects of type T
	 *
	 * To do this:
	 *  - The Tree map is already ordered by strata (depth) (Using the DeepestFirstPathComparator...).
	 *  - Elements in the tree, initially, represent compilation units.
	 *  - We iterate over tree at deepest depth first (call it n), collecting all the common macros together,
	 *    - We add the common macros to a new node representing the parent directory.
	 *    - repeating for all the children of paths n-1
	 *  - Add the n-1 directories to the map
	 *  - Iterate from n-1 until we hit the root.
	 */
	public static <T> void propogateUpwards(TreeMap<IPath, LinkedHashSet<T>> pathSortedMap) {

		if (pathSortedMap.isEmpty())
			return;

		// Create an index which demarkates the strata
		IPath index = new FixedDepthPathIndex(pathSortedMap.firstKey().segmentCount());
		pathSortedMap.put(index, new LinkedHashSet<T>());

		// Attribute all common macros between files and directories at a current level to the parent container directory
		while (index.segmentCount() > 0) {
			// Create the next index
			IPath nextIndex = new FixedDepthPathIndex(index.segmentCount()-1);
			pathSortedMap.put(nextIndex, new LinkedHashSet<T>());
			// Get a map of all entries at this depth
			SortedMap<IPath, LinkedHashSet<T>> currentDepthEntries = pathSortedMap.subMap(index, nextIndex);
			// Remove the lower bound index key
			pathSortedMap.remove(index);
			// Update index
			index = nextIndex;

			/** Temporary map for the parents */
			TreeMap<IPath, LinkedHashSet<T>> parentMap = new TreeMap<IPath, LinkedHashSet<T>>(new DeepestFirstPathComparator());

			// Find the common elements and add them to the parent directory
			IPath currentParentDir = null;
			LinkedHashSet<T> currenParentSet = null;
			for (Map.Entry<IPath, LinkedHashSet<T>> e : currentDepthEntries.entrySet()) {
				if (!e.getKey().removeLastSegments(1).equals(currentParentDir)) {
					// Add the parent to the map
					currentParentDir = e.getKey().removeLastSegments(1);
					currenParentSet = new LinkedHashSet<T>(e.getValue());
					parentMap.put(currentParentDir, currenParentSet);
				}
				currenParentSet.retainAll(e.getValue());
			}
			// Iterate again through the children removing the common elements
			for (Map.Entry<IPath, LinkedHashSet<T>> e : currentDepthEntries.entrySet())
				e.getValue().removeAll(parentMap.get(e.getKey().removeLastSegments(1)));
			// Add all the parents to the allMacrosMap
			pathSortedMap.putAll(parentMap);
		}

		// Remove all the CUs with no attributes
		Iterator<SortedMap.Entry<IPath, LinkedHashSet<T>>> it = pathSortedMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<IPath, LinkedHashSet<T>> e = it.next();
			if (e.getValue().isEmpty())
				it.remove();
		}

		if (DEBUG_PATH_TREE) {
			for (Map.Entry<IPath, LinkedHashSet<T>> e  : pathSortedMap.entrySet()) {
				Assert.isTrue(!(e.getKey() instanceof FixedDepthPathIndex));
				System.out.println(e.getKey());
				for (Object o : e.getValue())
					System.out.println(" " + o.toString());
			}
		}
	}

	/**
	 * Returns a sorted Map with parent paths first
	 * The Sorted set contains the unified set of attributes on the Path
	 * @param maps
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static Map<IPath, LinkedHashSet<Object>> combine(TreeMap... maps) {
		// Ensure that this set is sorted from the root elements going down
		Map<IPath, LinkedHashSet<Object>> result = new TreeMap<IPath, LinkedHashSet<Object>>(new ParentFirstPathComparator());
		if (maps.length == 0)
			return result;
		for (Map<IPath, LinkedHashSet> m : maps) {
			for (Map.Entry<IPath, LinkedHashSet> e : m.entrySet()) {
				if (!result.containsKey(e.getKey()))
					result.put(e.getKey(), new LinkedHashSet<Object>());
				result.get(e.getKey()).addAll(e.getValue());
			}
		}
		return result;
	}


}
