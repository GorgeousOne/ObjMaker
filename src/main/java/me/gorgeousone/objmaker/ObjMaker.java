package me.gorgeousone.objmaker;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public final class ObjMaker extends JavaPlugin implements Listener {

	@Override
	public void onEnable() {

		PluginManager manager = Bukkit.getPluginManager();
		manager.registerEvents(this, this);
	}

	@EventHandler
	public void onBlockClick(PlayerInteractEvent event) {
		if(event.getAction() != Action.LEFT_CLICK_BLOCK) {
			return;
		}

		Player player = event.getPlayer();
		ItemStack hand = player.getInventory().getItemInMainHand();

		if (hand.getType() != Material.STONE_AXE) {
			return;
		}

		detectBody(event.getClickedBlock());
	}

	BlockFace[] relatives = {
			BlockFace.UP,
			BlockFace.DOWN,
			BlockFace.SOUTH,
			BlockFace.NORTH,
			BlockFace.EAST,
			BlockFace.WEST
	};

	Vector[] xRelatives = {
		new Vector(0,  1,  0),
		new Vector(0,  1,  1),
		new Vector(0,  0,  1),
		new Vector(0, -1,  1),
		new Vector(0, -1,  0),
		new Vector(0, -1, -1),
		new Vector(0,  0, -1),
		new Vector(0,  1, -1),
	};

	Vector[] yRelatives = {
		new Vector( 1, 0,  0),
		new Vector( 1, 0,  1),
		new Vector( 0, 0,  1),
		new Vector(-1, 0,  1),
		new Vector(-1, 0,  0),
		new Vector(-1, 0, -1),
		new Vector( 0, 0, -1),
		new Vector( 1, 0, -1),
	};

	Vector[] zRelatives = {
		new Vector( 1,  0, 0),
		new Vector( 1,  1, 0),
		new Vector( 0,  1, 0),
		new Vector(-1,  1, 0),
		new Vector(-1,  0, 0),
		new Vector(-1, -1, 0),
		new Vector( 0, -1, 0),
		new Vector( 1, -1, 0),
	};


	Vector min;
	Vector max;
	List<Block> bodyBlocks;
	List<Block> surfaceBlocks;

	List<Vector> vertices;
	List<Face> faces;

	/**
	 * Detects all blocks connected directly to this block.
	 * Blocks touching air are stored as surface blocks
	 *
	 */
	void detectBody(Block block) {
//		min = block.getLocation().toVector();
//		max = block.getLocation().toVector();

		bodyBlocks = new ArrayList<>();
		surfaceBlocks = new ArrayList<>();
		vertices = new ArrayList<>();

		List<Block> newBlocks = new ArrayList<>();
		newBlocks.add(block);

		while (!newBlocks.isEmpty()) {
			Block nextBlock = newBlocks.get(0);
			bodyBlocks.add(nextBlock);
			newBlocks.remove(0);

			for (BlockFace face : relatives) {
				Block neighbor = nextBlock.getRelative(face);

					if (!neighbor.getType().isSolid()) {
					if (!surfaceBlocks.contains(nextBlock)) {
						surfaceBlocks.add(nextBlock);
						vertices.add(getCenter(nextBlock));
					}
					continue;
				}
				//new found blocks are stored like unfinished paths in a maze
				if (!bodyBlocks.contains(neighbor) && !newBlocks.contains(neighbor)) {
					newBlocks.add(neighbor);
//					Vector loc = neighbor.getLocation().toVector();
//					minimize(min, loc);
//					maximize(max, loc);
				}
			}
		}
	}

//	void minimize(Vector v, Vector other) {
//		v.setX(Math.min(v.getX(), other.getX()));
//		v.setX(Math.min(v.getX(), other.getX()));
//		v.setX(Math.min(v.getX(), other.getX()));
//	}
//
//	void maximize(Vector v, Vector other) {
//		v.setX(Math.max(v.getX(), other.getX()));
//		v.setX(Math.max(v.getX(), other.getX()));
//		v.setX(Math.max(v.getX(), other.getX()));
//	}

	Set<Block> getNeighbors(Block block, boolean areSolid, BlockFace excluded) {
		Set<Block> neighbors = new HashSet<>();

		for (BlockFace face : relatives) {
			if (face == excluded) {
				continue;
			}
			Block neighbor = block.getRelative(face);
			if (neighbor.getType().isSolid() == areSolid) {
				neighbors.add(neighbor);
			}
		}
		return neighbors;
	}

	/**
	 * Creates a mesh of faces by connecting the centers of surface blocks
	 */
	void createFaces() {
		faces = new ArrayList<>();
		Block nextBlock = surfaceBlocks.get(0);

		//iterate though the faces of the block touching air
		for (BlockFace face : relatives) {
			Block neighbor = nextBlock.getRelative(face);

			if (neighbor.getType().isSolid()) {
				continue;
			}

			Vector[] dirSet;
			if (face == BlockFace.EAST || face == BlockFace.WEST) {
				dirSet = xRelatives;
			} else if (face == BlockFace.UP || face == BlockFace.DOWN) {
				dirSet = yRelatives;
			} else {
				dirSet = zRelatives;
			}

			//get the 8 surrounding blocks around that open face
			Vector[] ring = new Vector[8];
			for (int i = 0; i < dirSet.length; i++) {
				Vector dir = dirSet[i];
				Block ringBlock = nextBlock.getRelative(dir.getBlockX(), dir.getBlockY(), dir.getBlockZ());

				//raise a block by 1 in the ring, if there is a block on top in facing direction
				if (ringBlock.getType().isSolid()) {
					Block extendedRingBlock = ringBlock.getRelative(face);
					ring[i] = getCenter(extendedRingBlock.getType().isSolid() ? extendedRingBlock : ringBlock);
				}
			}

			//create a face between each 2 solid ring blocks and the center
			int centerIndex = surfaceBlocks.indexOf(nextBlock);
			for (int i = 0; i < dirSet.length; i++) {
				if (ring[i] == null) {
					continue;
				}else if (ring[(i+1)%8] == null) {
					i++;
					continue;
				}
				addFace(new Face(
						centerIndex,
						vertices.indexOf(ring[i]),
						vertices.indexOf(ring[(i+1)%8])));
			}
		}
	}

	Vector getCenter(Block block) {
		return block.getLocation().add(0.5, 0.5, 0.5).toVector();
	}

	void addFace(Face newFace) {
		if (faces.contains(newFace)) {
			return;
		}
		faces.add(newFace);
	}

	/**
	 * Returns true if 2 faces partly or completely intersect with ecah other
	 */
	boolean facesOverlap(Face face1, Face face2) {
		List<Vector> matchingVs = new ArrayList<>();
		Vector notMatchingV1 = null;
		Vector notMatchingV2 = null;

		for (int v : face1.getIndices()) {
			if (face2.contains(v)) {
				matchingVs.add(vertices.get(v));
			}else {
				notMatchingV1 = vertices.get(v);
			}
		}
		if (matchingVs.size() == 3) {
			return true;
		}
		if (matchingVs.size() != 2) {
			return false;
		}
		//checks if the two overlapping vertices of the faces are close to each other
		if (distSquared(matchingVs.get(0), matchingVs.get(1)) != 1) {
			return false;
		}

		for (Vector v : face2.getVertices(vertices)) {
			if (!matchingVs.contains(v)) {
				notMatchingV2 = v;
				break;
			}
		}
		//if also the not matching vertices are next to each other the faces must overlap like in a rectangle (I think)
		return distSquared(notMatchingV1, notMatchingV2) == 1;
	}

	double distSquared(Vector v1, Vector v2) {
		return v1.clone().subtract(v2).lengthSquared();
	}

	void toObj(String path) {
		try {
			PrintWriter writer = new PrintWriter(new FileWriter(path));

			for (Vector vertex : vertices) {
				writer.println(String.format("v %f %f %f", vertex.getX(), vertex.getY(), vertex.getZ()));
			}
			writer.println();

			for (Face face : faces) {
				writer.println(face.objString());
			}
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	class Face {
		final int v0;
		final int v1;
		final int v2;

		Face(int v0, int v1, int v2) {
			this.v0 = v0;
			this.v1 = v1;
			this.v2 = v2;
		}

		boolean contains(int v) {
			return v0 == v || v1 == v || v2 == v;
		}

		int[] getIndices() {
			return new int[] {v0, v1, v2};
		}

		Vector[] getVertices(List<Vector> indexList) {
			return new Vector[] {indexList.get(v0), indexList.get(v1), indexList.get(v2)};
		}

		String objString() {
			return String.format("f %d %d %d", v0, v1, v2);
		}

		@Override
 		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Face)) return false;

			Face face = (Face) o;
			return face.contains(v0) && face.contains(v1) && face.contains(v2);
		}
	}
}
