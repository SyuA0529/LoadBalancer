package blog.syua.node.group;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import blog.syua.node.node.UdpNode;

@DisplayName("UDP NodeGroup 테스트")
class UdpNodeGroupTest {

	private static final int TEST_PORT = 10003;

	private UdpNodeGroup udpNodeManager;

	@BeforeEach
	void beforeEach() throws SocketException {
		udpNodeManager = new UdpNodeGroup(TEST_PORT);
	}

	@Nested
	@DisplayName("Method: startForward")
	class MethodStartForward {
		@Test
		@DisplayName("등록된 UDP 노드로 포워딩을 수행한다")
		void forwardToRegisteredUdpNode() throws IOException {
			//given
			UdpNode mockedUdpNode = new UdpNode(InetAddress.getLocalHost().getHostAddress(), TEST_PORT) {
				@Override
				public void forwardPacket(DatagramSocket loadBalancerSocket, DatagramPacket clientPacket) {
					try {
						byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
						DatagramPacket packet = new DatagramPacket(data, data.length, clientPacket.getAddress(),
							clientPacket.getPort());
						loadBalancerSocket.send(packet);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			};
			udpNodeManager.registerNode(mockedUdpNode);

			//when
			udpNodeManager.startForwarding();
			String result = sendDataToNodeManager();

			//then
			assertThat(result).isEqualTo("Hello");
		}
	}

	@Nested
	@DisplayName("Method: unRegisterNode")
	class MethodUnRegisterNode {
		@Test
		@DisplayName("UDP 노드가 존재하지 않을 경우 포워딩을 시작할 수 없다")
		void cannotStartForwarding() throws UnknownHostException {
			//given
			UdpNode udpNode = new UdpNode(InetAddress.getLocalHost().getHostAddress(), 0);
			udpNodeManager.registerNode(udpNode);

			//when
			udpNodeManager.unRegisterNode(udpNode);

			//then
			assertThatThrownBy(() -> udpNodeManager.startForwarding())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("포워딩을 시작할 수 없습니다");
		}

		@Test
		@DisplayName("UDP 노드가 존재하지 않는 경우 더이상 새로운 요청을 처리하지 않는다")
		void doNotProcessNewRequest() throws InterruptedException, IOException {
			//given
			UdpNode udpNode = new UdpNode(InetAddress.getLocalHost().getHostAddress(), TEST_PORT);
			udpNodeManager.registerNode(udpNode);
			udpNodeManager.startForwarding();
			Thread.sleep(1000);

			//when
			udpNodeManager.unRegisterNode(udpNode);

			//then
			assertThatThrownBy(UdpNodeGroupTest::sendDataToNodeManager)
				.isInstanceOf(SocketTimeoutException.class)
				.hasMessage("Receive timed out");
		}
	}

	private static String sendDataToNodeManager() throws IOException {
		byte[] data = "client data".getBytes(StandardCharsets.UTF_8);
		DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), TEST_PORT);
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(3000);
		socket.send(packet);

		DatagramPacket resultPacket = new DatagramPacket(new byte[socket.getReceiveBufferSize()],
			socket.getReceiveBufferSize());
		socket.receive(resultPacket);
		socket.close();
		return new String(removeTrailingZeros(resultPacket.getData()), StandardCharsets.UTF_8);
	}

	private static byte[] removeTrailingZeros(byte[] data) {
		int endIndex = data.length - 1;
		while (endIndex >= 0 && data[endIndex] == 0) {
			endIndex--;
		}
		return Arrays.copyOf(data, endIndex + 1);
	}

}