package smp.aeternum.carpet.materials;

import java.util.List;

public class MaterialList {
	public String name;
	public List<MaterialEntry> entries;
	public int scannedAvailable;
	public long nextScanAt;

	public MaterialList() {}
	public MaterialList(String name, List<MaterialEntry> entries) { this.name = name; this.entries = entries; }
}
