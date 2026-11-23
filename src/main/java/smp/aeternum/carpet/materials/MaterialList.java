package smp.aeternum.carpet.materials;

import java.util.ArrayList;
import java.util.List;

public class MaterialList {
	public String name;
	public List<MaterialEntry> entries;
	public int scannedAvailable;
	public long nextScanAt;

	// Unified project / Syncmatica-lite fields
	public boolean hasSchematic;
	public String uploadedBy;
	public int totalBlocks;
	public int placedBlocks;
	public PlacementInfo placement;
	public List<SubregionInfo> subregions = new ArrayList<>();

	public MaterialList() {}

	public MaterialList(String name, List<MaterialEntry> entries) {
		this.name = name;
		this.entries = entries;
	}

	public static class PlacementInfo {
		public boolean configured;
		public String dimension = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String rotation = "NONE";
		public String mirror = "NONE";
		public String subregionsJson = "[]";

		public PlacementInfo() {}

		public PlacementInfo(String dimension, int x, int y, int z, String rotation, String mirror) {
			this(dimension, x, y, z, rotation, mirror, "[]");
		}

		public PlacementInfo(String dimension, int x, int y, int z, String rotation, String mirror, String subregionsJson) {
			this.configured = true;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.rotation = rotation;
			this.mirror = mirror;
			this.subregionsJson = (subregionsJson != null && !subregionsJson.isBlank()) ? subregionsJson : "[]";
		}
	}

	public static class SubregionInfo {
		public String name;
		public int totalBlocks;
		public int placedBlocks;
		public String status = "NONE"; // NONE, CLAIMED, DONE
		public String claimedBy;
		public List<MaterialEntry> entries = new ArrayList<>();

		public SubregionInfo() {}

		public SubregionInfo(String name, int totalBlocks, List<MaterialEntry> entries) {
			this.name = name;
			this.totalBlocks = totalBlocks;
			this.entries = entries;
		}
	}
}
