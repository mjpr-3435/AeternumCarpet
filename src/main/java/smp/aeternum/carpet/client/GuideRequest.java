package smp.aeternum.carpet.client;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;import smp.aeternum.carpet.network.GuideEditPayload;
final class GuideRequest {private GuideRequest(){}static void send(String op,String title,String value){ClientPlayNetworking.send(new GuideEditPayload(op,title,value));}}
