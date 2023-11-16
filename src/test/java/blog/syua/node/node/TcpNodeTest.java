package blog.syua.node.node;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.healthcheck.dto.HealthCheckResponse;
import blog.syua.utils.NodeMessageUtil;
import blog.syua.utils.SocketReadUtils;
import nl.altindag.log.LogCaptor;

@DisplayName("TCP 노드 테스트")
class TcpNodeTest {

    private static final int MAX_DATA_SIZE = 8192;
    private static int FORWARD_PORT = 40010;
    private static int HEALTH_CHECK_PORT = 40020;

    private SoftAssertions softAssertions;
    private Socket clientSocket;
    private final byte[] clientSocketData = "Client Data".getBytes(StandardCharsets.UTF_8);
    private final ByteArrayOutputStream clientSocketOutputStream = new ByteArrayOutputStream(MAX_DATA_SIZE);

    @BeforeEach
    void beforeEach() throws IOException {
        softAssertions = new SoftAssertions();
        clientSocket = mock(Socket.class);
        when(clientSocket.getInputStream()).thenReturn(new ByteArrayInputStream(clientSocketData));
        when(clientSocket.getOutputStream()).thenReturn(clientSocketOutputStream);
        when(clientSocket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
        when(clientSocket.getPort()).thenReturn(5001);
    }

    @Nested
    @DisplayName("Method: forwardPacket")
    class MethodForwardPacket {
        @BeforeEach
        void setUp() {
            FORWARD_PORT += 1;
        }

        @Test
        @DisplayName("노드에게 받은 데이터를 포워딩한다")
        void forwardDataToNode() throws InterruptedException {
            //given
            Thread forwardNodeThread = getForwardNodeThread();
            forwardNodeThread.start();
            TcpNode forwardTcpNode = new TcpNode(InetAddress.getLoopbackAddress(), FORWARD_PORT);
            Thread.sleep(1000);

            //when
            forwardTcpNode.forwardPacket(clientSocket);

            //then
            String result = clientSocketOutputStream.toString(StandardCharsets.UTF_8);
            assertThat(result).isEqualTo("Hello");
            forwardNodeThread.interrupt();
        }

        @Test
        @DisplayName("노드에게 받은 데이터를 포워딩할 수 없는 경우 에러 메세지를 반환한다")
        void returnErrorMessage() throws IOException {
            //given
            TcpNode deadTcpNode = new TcpNode(InetAddress.getLocalHost(), FORWARD_PORT);

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
        @BeforeEach
        void setUp() {
            HEALTH_CHECK_PORT += 1;
        }

        @Test
        @DisplayName("노드가 살아있는 경우 true를 반환한다")
        void returnTrue() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
            //given
            TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetTcpNode);
            Thread nodeThread = getHealthCheckNodeThread(true);
            nodeThread.start();
            Thread.sleep(1000);

            //when
            //then
            assertThat(targetTcpNode.isHealthy()).isTrue();
            nodeThread.interrupt();
        }

        @Test
        @DisplayName("노드가 죽어있는 경우 false를 반환한다")
        void returnFalseWhenNodeDead() throws IOException, NoSuchFieldException, IllegalAccessException {
            //given
            TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetTcpNode);

            //when
            //then
            assertThat(targetTcpNode.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("노드가 보낸 응답의 파싱에 실패한 경우 false를 반환한다")
        void returnFalseWhenParsingFail() throws
            IOException,
            InterruptedException,
            NoSuchFieldException,
            IllegalAccessException {
            //given
            TcpNode targetTcpNode = new TcpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetTcpNode);
            Thread nodeThread = getHealthCheckNodeThread(false);
            nodeThread.start();
            LogCaptor logCaptor = LogCaptor.forClass(TcpNode.class);
            Thread.sleep(1000);

            //when
            //then
            softAssertions.assertThat(targetTcpNode.isHealthy()).isFalse();
            softAssertions.assertThat(logCaptor.getLogs()).anyMatch(log -> log.contains("Json Parsing Error"));
            softAssertions.assertAll();
            logCaptor.close();
            nodeThread.interrupt();
        }

        private void setHealthCheckPort(TcpNode targetTcpNode) throws NoSuchFieldException, IllegalAccessException {
            Field field = targetTcpNode.getClass().getSuperclass().getDeclaredField("healthCheckPort");
            field.setAccessible(true);
            field.set(targetTcpNode, HEALTH_CHECK_PORT);
        }
    }

    private Thread getForwardNodeThread() {
        return new Thread(() -> {
            try {
                ServerSocket nodeSocket = new ServerSocket(FORWARD_PORT);
                Socket clientSocket;
                while (Objects.nonNull(clientSocket = nodeSocket.accept())) {
                    InputStream inputStream = clientSocket.getInputStream();
                    OutputStream outputStream = clientSocket.getOutputStream();

                    SocketReadUtils.readTcpAllBytes(inputStream);
                    outputStream.write("Hello".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();

                    outputStream.close();
                    inputStream.close();
                    clientSocket.close();
                }
            } catch (IOException exception) {
                if (!exception.getMessage().equals("Interrupted function call: accept failed")) {
                    throw new RuntimeException(exception);
                }
            }
        });
    }

    private Thread getHealthCheckNodeThread(boolean isCorrect) {
        return new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(HEALTH_CHECK_PORT);
                Socket clientSocket;
                while (Objects.nonNull(clientSocket = serverSocket.accept())) {
                    InputStream inputStream = clientSocket.getInputStream();
                    SocketReadUtils.readTcpAllBytes(inputStream);
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