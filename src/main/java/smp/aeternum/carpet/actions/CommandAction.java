package smp.aeternum.carpet.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CommandAction {
    public String id = UUID.randomUUID().toString();
    public String name = "Nueva acción";
    public String description = "";
    public List<CommandStep> steps = new ArrayList<>();
}
