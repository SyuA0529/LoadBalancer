package blog.syua.node.node;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.healthcheck.dto.HealthCheckResponse;
import blog.syua.utils.NodeMessageUtil;
import nl.altindag.log.LogCaptor;

@DisplayName("UDP 노드 테스트")
class UdpNodeTest {

	private static final int TEST_PORT = 10001;
	private static DatagramSocket nodeServerSocket;
	private static boolean testing;

	private SoftAssertions softAssertions;

	private UdpNode udpNode;

	@BeforeAll
	static void beforeAll() throws SocketException {
		nodeServerSocket = new DatagramSocket(TEST_PORT);
		testing = true;
		new Thread(() -> {
			while (testing) {
				try {
					DatagramPacket packet = new DatagramPacket(
						new byte[nodeServerSocket.getReceiveBufferSize()], nodeServerSocket.getReceiveBufferSize());
					nodeServerSocket.receive(packet);
					byte[] returnData = "Hello".getBytes(StandardCharsets.UTF_8);
					System.out.println("Hello");
					nodeServerSocket.send(
						new DatagramPacket(returnData, returnData.length, packet.getAddress(), packet.getPort()));
				} catch (Exception e) {
					if (!(e instanceof SocketException)) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	@BeforeEach
	void beforeEach() throws IOException {
		softAssertions = new SoftAssertions();
		udpNode = new UdpNode(InetAddress.getLoopbackAddress(), TEST_PORT);
	}

	@AfterAll
	static void afterAll() {
		testing = false;
		nodeServerSocket.close();
	}

	@Nested
	@DisplayName("Method: forwardPacket")
	class MethodForwardPacket {
		@Test
		@DisplayName("노드에게 받은 패킷을 포워딩한다")
		void forwardPacketToNode() throws SocketException {
			//given
			byte[] clientData = "client data".getBytes(StandardCharsets.UTF_8);
			DatagramPacket clientPacket = new DatagramPacket(clientData, clientData.length,
				InetAddress.getLoopbackAddress(), TEST_PORT);
			final DatagramPacket[] resultPacket = new DatagramPacket[1];

			//when
			udpNode.forwardPacket(new DatagramSocket(6000) {
				@Override
				public void send(DatagramPacket packet) {
					resultPacket[0] = packet;
				}
			}, clientPacket);

			//then
			assertThat(new String(resultPacket[0].getData(), StandardCharsets.UTF_8).replace("\u0000", ""))
				.isEqualTo("Hello");
		}

		@Test
		@DisplayName("노드에게 받은 데이터를 포워딩할 수 없는 경우 에러 메세지를 반환한다")
		void returnErrorMessage() throws IOException {
			//given
			UdpNode deadUdpNode = new UdpNode(InetAddress.getLocalHost(), 30000);
			byte[] clientData = "client data".getBytes(StandardCharsets.UTF_8);
			DatagramPacket clientPacket = new DatagramPacket(clientData, clientData.length,
				InetAddress.getLoopbackAddress(), TEST_PORT);
			final DatagramPacket[] resultPacket = new DatagramPacket[1];

			//when
			deadUdpNode.forwardPacket(new DatagramSocket(6001) {
				@Override
				public void send(DatagramPacket packet) {
					resultPacket[0] = packet;
				}
			}, clientPacket);

			//then
			assertThat(new String(resultPacket[0].getData(), StandardCharsets.UTF_8).replace("\u0000", ""))
				.isEqualTo(new String(NodeMessageUtil.getForwardErrorMessage(), StandardCharsets.UTF_8));
		}
	}

	@Nested
	@DisplayName("Method: isHealthy")
	class MethodIsHealthy {
		@Test
		@DisplayName("노드가 살아있는 경우 true를 반환한다")
		void returnTrue() throws IOException {
			//given
			UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), 20000);
			Thread nodeThread = getHealthCheckNodeThread(20000, true);
			nodeThread.start();

			//when
			//then
			assertThat(targetUdpNode.isHealthy()).isTrue();
		}

		@Test
		@DisplayName("노드가 죽어있는 경우 false를 반환한다")
		void returnFalse() throws IOException {
			//given
			UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), 0);

			//when
			//then
			assertThat(targetUdpNode.isHealthy()).isFalse();
		}

		@Test
		@DisplayName("노드가 보낸 응답의 파싱에 실패한 경우 false를 반환한다")
		void returnFalseWhenParsingFail() throws IOException {
			//given
			UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), 20001);
			Thread nodeThread = getHealthCheckNodeThread(20001, false);
			nodeThread.start();
			LogCaptor logCaptor = LogCaptor.forClass(UdpNode.class);

			//when
			//then
			softAssertions.assertThat(targetUdpNode.isHealthy()).isFalse();
			softAssertions.assertThat(logCaptor.getLogs()).anyMatch(log -> log.contains("Json Parsing Error"));
			softAssertions.assertAll();
			logCaptor.close();
			nodeThread.interrupt();
		}

		private Thread getHealthCheckNodeThread(int port, boolean isCorrect) {
			return new Thread(() -> {
				try (DatagramSocket serverSocket = new DatagramSocket(port)) {
					DatagramPacket clientPacket = new DatagramPacket(new byte[serverSocket.getReceiveBufferSize()],
						serverSocket.getReceiveBufferSize());
					while (true) {
						serverSocket.receive(clientPacket);
						byte[] responseBytes;
						if (isCorrect) {
							HealthCheckResponse response = new HealthCheckResponse();
							response.setHealthy();
							responseBytes = new ObjectMapper().writeValueAsBytes(response);
						} else {
							responseBytes = "Wrong".getBytes(StandardCharsets.UTF_8);
						}
						DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length,
							clientPacket.getAddress(), clientPacket.getPort());
						serverSocket.send(responsePacket);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

}