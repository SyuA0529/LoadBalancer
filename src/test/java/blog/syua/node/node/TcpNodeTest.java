package blog.syua.node.node;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.healthcheck.HealthCheckResponse;
import blog.syua.utils.NodeMessageUtil;
import nl.altindag.log.LogCaptor;

@DisplayName("TCP 노드 테스트")
class TcpNodeTest {

	private static final int MAX_DATA_SIZE = 8192;
	private static final int TEST_PORT = 10000;

	private static ServerSocket nodeSocket;
	private static boolean testing;

	private SoftAssertions softAssertions;

	@Mock
	private Socket clientSocket;
	private final byte[] clientSocketData = new byte[MAX_DATA_SIZE];
	private final ByteArrayOutputStream clientSocketOutputStream = new ByteArrayOutputStream(MAX_DATA_SIZE);
	private TcpNode tcpNode;

	@BeforeAll
	static void beforeAll() throws IOException {
		nodeSocket = new ServerSocket(TEST_PORT);
		testing = true;
		new Thread(() -> {
			while (testing) {
				try {
					Socket socket = nodeSocket.accept();
					InputStream inputStream = socket.getInputStream();
					OutputStream outputStream = socket.getOutputStream();

					inputStream.readAllBytes();
					outputStream.write("Hello".getBytes(StandardCharsets.UTF_8));
					outputStream.flush();

					outputStream.close();
					inputStream.close();
					socket.close();
				} catch (IOException exception) {
					if (!exception.getMessage().equals("Interrupted function call: accept failed")) {
						throw new RuntimeException(exception);
					}
				}
			}
		}).start();
	}

	@BeforeEach
	void beforeEach() throws IOException {
		softAssertions = new SoftAssertions();
		tcpNode = new TcpNode(InetAddress.getLoopbackAddress().getHostAddress(), TEST_PORT);
		clientSocket = mock(Socket.class);
		when(clientSocket.getInputStream()).thenReturn(new ByteArrayInputStream(clientSocketData));
		when(clientSocket.getOutputStream()).thenReturn(clientSocketOutputStream);
		when(clientSocket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
		when(clientSocket.getPort()).thenReturn(5001);
	}

	@AfterAll
	static void afterAll() throws IOException {
		testing = false;
		nodeSocket.close();
	}

	@Nested
	@DisplayName("Method: forwardPacket")
	class MethodForwardPacket {
		@Test
		@DisplayName("노드에게 받은 데이터를 포워딩한다")
		void forwardDataToNode() {
			//given
			//when
			tcpNode.forwardPacket(clientSocket);

			//then
			String result = clientSocketOutputStream.toString(StandardCharsets.UTF_8);
			assertThat(result).isEqualTo("Hello");
		}

		@Test
		@DisplayName("노드에게 받은 데이터를 포워딩할 수 없는 경우 에러 메세지를 반환한다")
		void returnErrorMessage() throws IOException {
			//given
			TcpNode deadTcpNode = new TcpNode(InetAddress.getLocalHost().getHostAddress(), 0);

			//when
			deadTcpNode.forwardPacket(clientSocket);

			//then
			byte[] result = clientSocketOutputStream.toByteArray();
			assertThat(result).containsExactly(NodeMessageUtil.getForwardErrorMessage());
		}
	}

	@Nested
	@DisplayName("Method: isHealthy")
	class MethodIsHealthy {
		@Test
		@DisplayName("노드가 살아있는 경우 true를 반환한다")
		void returnTrue() throws IOException {
			//given
			TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost().getHostAddress(), 20000);
			Thread nodeThread = getHealthCheckNodeThread(20000, true);
			nodeThread.start();

			//when
			//then
			assertThat(targetTcpNode.isHealthy()).isTrue();
			nodeThread.interrupt();
		}

		@Test
		@DisplayName("노드가 죽어있는 경우 false를 반환한다")
		void returnFalseWhenNodeDead() throws IOException {
			//given
			TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost().getHostAddress(), 20000);

			//when
			//then
			assertThat(targetTcpNode.isHealthy()).isFalse();
		}

		@Test
		@DisplayName("노드가 보낸 응답의 파싱에 실패한 경우 false를 반환한다")
		void returnFalseWhenParsingFail() throws IOException {
			//given
			TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost().getHostAddress(), 20001);
			Thread nodeThread = getHealthCheckNodeThread(20001, false);
			nodeThread.start();
			LogCaptor logCaptor = LogCaptor.forClass(TcpNode.class);

			//when
			//then
			softAssertions.assertThat(targetTcpNode.isHealthy()).isFalse();
			softAssertions.assertThat(logCaptor.getLogs()).anyMatch(log -> log.contains("Json Parsing Error"));
			softAssertions.assertAll();
			logCaptor.close();
			nodeThread.interrupt();
		}

		private Thread getHealthCheckNodeThread(int port, boolean isCorrect) {
			return new Thread(() -> {
				try {
					ServerSocket serverSocket = new ServerSocket(port);
					Socket clientSocket;
					while (Objects.nonNull(clientSocket = serverSocket.accept())) {
						InputStream inputStream = clientSocket.getInputStream();
						// inputStream.readAllBytes();
						OutputStream outputStream = clientSocket.getOutputStream();
						if (isCorrect) {
							HealthCheckResponse response = new HealthCheckResponse();
							response.setHealthy();
							outputStream.write(new ObjectMapper().writeValueAsBytes(response));
						} else {
							outputStream.write("Wrong".getBytes(StandardCharsets.UTF_8));
						}
						outputStream.flush();
						inputStream.close();
						outputStream.close();
						clientSocket.close();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

}