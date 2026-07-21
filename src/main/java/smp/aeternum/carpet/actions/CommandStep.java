package smp.aeternum.carpet.actions;

/** A server command, or an explicit non-blocking delay between commands. */
public final class CommandStep {
    public enum Type { COMMAND, WAIT }

    public Type type = Type.COMMAND;
    public String value = "";

    public CommandStep() { }
    public CommandStep(Type type, String value) { this.type = type; this.value = value; }
}
