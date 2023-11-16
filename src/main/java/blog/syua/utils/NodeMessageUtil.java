package blog.syua.utils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import blog.syua.node.node.Protocol;

public class NodeMessageUtil {

	private static final List<String> TCP_SOCKET_INTERRUPT_MESSAGE = List.of("Interrupted function call: accept failed", "Socket closed");
	private static final List<String> UDP_SOCKET_INTERRUPT_MESSAGE = List.of("socket closed");
	private static final String FORWARD_ERROR_MESSAGE = "Packet forwarding failed";

	private NodeMessageUtil() {
	}

	public static List<String> getSocketInterruptMessage(Protocol protocol) {
		if (protocol.equals(Protocol.TCP)) {
			return TCP_SOCKET_INTERRUPT_MESSAGE;
		}
		if (protocol.equals(Protocol.UDP)) {
			return UDP_SOCKET_INTERRUPT_MESSAGE;
		}
		return null;
	}

	public static byte[] getForwardErrorMessage() {
		return FORWARD_ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8);
	}

}
