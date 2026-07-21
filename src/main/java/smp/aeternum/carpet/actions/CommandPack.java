package smp.aeternum.carpet.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named collection of actions. It replaces a YAML command file. */
public final class CommandPack {
    public String id = UUID.randomUUID().toString();
    public String name = "Nuevo paquete";
    public String description = "";
    /** Ordered contents of this package. A package is directly executable. */
    public List<CommandStep> commands = new ArrayList<>();
    /** Kept only to import catalogs written by the first development build. */
    public List<CommandAction> actions = new ArrayList<>();
}
