package blog.syua.node.node;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.healthcheck.HealthCheckResponse;

@DisplayName("TCP 노드 테스트")
class TcpNodeTest {

	private static final int MAX_DATA_SIZE = 8192;
	private static final int TEST_PORT = 10000;

	private static ServerSocket nodeSocket;
	private static boolean testing;

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
					OutputStream outputStream = socket.getOutputStream();
					outputStream.write("Hello".getBytes(StandardCharsets.UTF_8));
					outputStream.close();
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
	}

	@Nested
	@DisplayName("Method: isHealthy")
	class MethodIsHealthy {
		private TcpNode targetTcpNode;

		@BeforeEach
		void beforeEach() throws IOException {
			targetTcpNode = new TcpNode(InetAddress.getLocalHost().getHostAddress(), 20000);
		}

		@Test
		@DisplayName("노드가 살아있는 경우 true를 반환한다")
		void returnTrue() {
			//given
			Thread nodeThread = getHealthCheckNodeThread();
			nodeThread.start();

			//when
			//then
			assertThat(targetTcpNode.isHealthy()).isTrue();
			nodeThread.interrupt();
		}

		@Test
		@DisplayName("노드가 죽어있는 경우 false를 반환한다")
		void returnFalse() {
			//given
			//when
			//then
			assertThat(targetTcpNode.isHealthy()).isFalse();
		}

		private Thread getHealthCheckNodeThread() {
			return new Thread(() -> {
				try {
					ServerSocket serverSocket = new ServerSocket(20000);
					Socket clientSocket;
					while (Objects.nonNull(clientSocket = serverSocket.accept())) {
						OutputStream outputStream = clientSocket.getOutputStream();
						HealthCheckResponse response = new HealthCheckResponse();
						response.setHealthy();
						outputStream.write(new ObjectMapper().writeValueAsBytes(response));
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