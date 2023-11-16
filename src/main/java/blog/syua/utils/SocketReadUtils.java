package blog.syua.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;

import blog.syua.node.node.Protocol;

public class SocketReadUtils {

	private SocketReadUtils() {
	}

	public static byte[] readTcpAllBytes(InputStream inputStream) throws IOException {
		byte[] buffer = new byte[Protocol.TCP.getMaxReceiveSize()];
		ByteBuffer byteBuffer = ByteBuffer.allocate(Protocol.TCP.getMaxReceiveSize());
		int readSize;
		while ((readSize = inputStream.read(buffer)) == buffer.length) {
			putToByteBuffer(byteBuffer, buffer, readSize);
		}
		putToByteBuffer(byteBuffer, buffer, readSize);
		byte[] returnBytes = NodeMessageUtil.removeTrailingZeros(byteBuffer.array());
		byteBuffer.clear();
		return returnBytes;
	}

	public static DatagramPacket readUdpAllBytes(DatagramSocket socket) throws IOException {
		DatagramPacket resultPacket = new DatagramPacket(new byte[Protocol.UDP.getMaxReceiveSize()],
			Protocol.UDP.getMaxReceiveSize());
		socket.receive(resultPacket);
		return resultPacket;
	}

	private static void putToByteBuffer(ByteBuffer byteBuffer, byte[] buffer, int length) {
		byteBuffer.put(buffer, 0, length);
		Arrays.fill(buffer, (byte)0);
	}

}
