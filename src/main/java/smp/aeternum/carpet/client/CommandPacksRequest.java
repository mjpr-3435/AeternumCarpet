package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.network.CommandActionRunPayload;
import smp.aeternum.carpet.network.CommandPacksUpdatePayload;
import java.util.List;

final class CommandPacksRequest {
    private static final Gson GSON = new Gson();
    private CommandPacksRequest() { }
    static void save(List<CommandPack> packs) { ClientPlayNetworking.send(new CommandPacksUpdatePayload(GSON.toJson(packs))); }
    static void run(CommandPack pack) { ClientPlayNetworking.send(new CommandActionRunPayload(pack.id)); }
}
