package smp.aeternum.carpet.materials;

public class MaterialEntry {
	public String itemName;
	public String itemId;
	public int total;
	public int available;
	public int placed;
	public String claimedBy;

	public MaterialEntry() {}

	public MaterialEntry(String name, String id, int total, int available) {
		this.itemName = name;
		this.itemId = id;
		this.total = total;
		this.available = available;
	}

	public int missing() {
		return Math.max(0, total - placed - available);
	}
}
