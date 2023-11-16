package blog.syua.node.group;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import blog.syua.node.node.TcpNode;

@DisplayName("TCP NodeGroup 테스트")
class TcpNodeGroupTest {

	private static int TEST_PORT = 20020;

	private TcpNodeGroup tcpNodeManager;

	@BeforeEach
	void beforeEach() throws IOException {
		TEST_PORT += 1;
		tcpNodeManager = new TcpNodeGroup(TEST_PORT);
	}

	@Nested
	@DisplayName("Method: startForward")
	class MethodStartForward {
		@Test
		@DisplayName("등록된 TCP 노드로 포워딩을 수행한다")
		void forwardToRegisteredTcpNode() throws IOException, InterruptedException {
			//given
			TcpNode mockedUdpNode = new TcpNode(InetAddress.getLocalHost(), TEST_PORT) {
				@Override
				public void forwardPacket(Socket clientSocket) {
					try {
						OutputStream outputStream = clientSocket.getOutputStream();
						outputStream.write("Hello".getBytes(StandardCharsets.UTF_8));
						outputStream.flush();
						clientSocket.shutdownOutput();
						outputStream.close();
						clientSocket.close();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			};
			tcpNodeManager.registerNode(mockedUdpNode);

			//when
			tcpNodeManager.startForwarding();
			Thread.sleep(1000);

			//then
			assertThat(sendDataToNodeGroup()).isEqualTo("Hello");
		}
	}

	@Nested
	@DisplayName("Method: unRegisterNode")
	class MethodUnRegisterNode {
		@Test
		@DisplayName("TCP 노드가 존재하지 않을 경우 포워딩를 시작할 수 없다")
		void cannotStartForward() throws IOException {
			//given
			TcpNode tcpNode = new TcpNode(InetAddress.getLocalHost(), TEST_PORT);
			tcpNodeManager.registerNode(tcpNode);

			//when
			tcpNodeManager.unRegisterNode(tcpNode);

			//then
			assertThatThrownBy(() -> tcpNodeManager.startForwarding())
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to start forwarding");
		}

		@Test
		@DisplayName("TCP 노드가 존재하지 않는 경우 더이상 새로운 요청을 처리하지 않는다")
		void doNotProcessNewRequest() throws InterruptedException, IOException {
			//given
			TcpNode tcpNode = new TcpNode(InetAddress.getLocalHost(), TEST_PORT);
			tcpNodeManager.registerNode(tcpNode);
			tcpNodeManager.startForwarding();
			Thread.sleep(1000);

			//when
			tcpNodeManager.unRegisterNode(tcpNode);

			//then
			assertThatThrownBy(TcpNodeGroupTest.this::sendDataToNodeGroup)
				.isInstanceOf(SocketException.class);
		}
	}

	private String sendDataToNodeGroup() throws IOException {
		Socket clientSocket = new Socket(InetAddress.getLocalHost(), TEST_PORT);
		OutputStream outputStream = clientSocket.getOutputStream();
		outputStream.write("Client Data".getBytes(StandardCharsets.UTF_8));
		outputStream.flush();
		InputStream inputStream = clientSocket.getInputStream();
		String result = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		outputStream.close();
		inputStream.close();
		clientSocket.close();
		return result;
	}

}