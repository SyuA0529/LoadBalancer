package blog.syua.node.node;

import blog.syua.healthcheck.dto.HealthCheckResponse;
import blog.syua.utils.NodeMessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UDP 노드 테스트")
class UdpNodeTest {

    private static int FORWARD_PORT = 40020;
    private static int HEALTH_CHECK_PORT = 40020;

    private SoftAssertions softAssertions;

    @BeforeEach
    void beforeEach() {
        softAssertions = new SoftAssertions();
        FORWARD_PORT += 1;
        HEALTH_CHECK_PORT += 1;
    }

    @Nested
    @DisplayName("Method: forwardPacket")
    class MethodForwardPacket {
        @Test
        @DisplayName("노드에게 받은 패킷을 포워딩한다")
        void forwardPacketToNode() throws SocketException, InterruptedException {
            //given
            Thread forwardNodeThread = getForwardNodeThread();
            forwardNodeThread.start();
            UdpNode udpNode = new UdpNode(InetAddress.getLoopbackAddress(), FORWARD_PORT);
            byte[] clientData = "client data".getBytes(StandardCharsets.UTF_8);
            DatagramPacket clientPacket = new DatagramPacket(clientData, clientData.length,
                    InetAddress.getLoopbackAddress(), FORWARD_PORT);
            final DatagramPacket[] resultPacket = new DatagramPacket[1];
            Thread.sleep(1000);

            //when
            udpNode.forwardPacket(new DatagramSocket(FORWARD_PORT + 20) {
                @Override
                public void send(DatagramPacket packet) {
                    resultPacket[0] = packet;
                }
            }, clientPacket);

            //then
            assertThat(new String(resultPacket[0].getData(), StandardCharsets.UTF_8).replace("\u0000", ""))
                    .isEqualTo("Hello");
            forwardNodeThread.interrupt();
        }

        @Test
        @DisplayName("노드에게 받은 데이터를 포워딩할 수 없는 경우 에러 메세지를 반환한다")
        void returnErrorMessage() throws IOException {
            //given
            UdpNode deadUdpNode = new UdpNode(InetAddress.getLocalHost(), FORWARD_PORT);
            byte[] clientData = "client data".getBytes(StandardCharsets.UTF_8);
            DatagramPacket clientPacket = new DatagramPacket(clientData, clientData.length,
                    InetAddress.getLoopbackAddress(), FORWARD_PORT + 11);
            final DatagramPacket[] resultPacket = new DatagramPacket[1];

            //when
            deadUdpNode.forwardPacket(new DatagramSocket(FORWARD_PORT) {
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
        void returnTrue() throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
            //given
            UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetUdpNode);
            Thread nodeThread = getHealthCheckNodeThread(true);
            nodeThread.start();
            Thread.sleep(1000);

            //when
            //then
            assertThat(targetUdpNode.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("노드가 죽어있는 경우 false를 반환한다")
        void returnFalse() throws IOException, NoSuchFieldException, IllegalAccessException {
            //given
            UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetUdpNode);

            //when
            //then
            assertThat(targetUdpNode.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("노드가 보낸 응답의 파싱에 실패한 경우 false를 반환한다")
        void returnFalseWhenParsingFail() throws
            IOException,
            InterruptedException,
            NoSuchFieldException,
            IllegalAccessException {
            //given
            UdpNode targetUdpNode = new UdpNode(InetAddress.getLocalHost(), HEALTH_CHECK_PORT);
            setHealthCheckPort(targetUdpNode);
            Thread nodeThread = getHealthCheckNodeThread(false);
            nodeThread.start();
            LogCaptor logCaptor = LogCaptor.forClass(UdpNode.class);
            Thread.sleep(1000);

            //when
            //then
            softAssertions.assertThat(targetUdpNode.isHealthy()).isFalse();
            softAssertions.assertThat(logCaptor.getLogs()).anyMatch(log -> log.contains("Json Parsing Error"));
            softAssertions.assertAll();
            logCaptor.close();
            nodeThread.interrupt();
        }

        private void setHealthCheckPort(UdpNode targetUdpNode) throws NoSuchFieldException, IllegalAccessException {
            Field field = targetUdpNode.getClass().getSuperclass().getDeclaredField("healthCheckPort");
            field.setAccessible(true);
            field.set(targetUdpNode, HEALTH_CHECK_PORT);
        }
    }

    private Thread getForwardNodeThread() {
        return new Thread(() -> {
            try (DatagramSocket nodeServerSocket = new DatagramSocket(FORWARD_PORT)) {
                DatagramPacket clientPacket = new DatagramPacket(new byte[Protocol.UDP.getMaxReceiveSize()],
                    Protocol.UDP.getMaxReceiveSize());
                while (true) {
                    nodeServerSocket.receive(clientPacket);
                    byte[] returnData = "Hello".getBytes(StandardCharsets.UTF_8);
                    nodeServerSocket.send(
                            new DatagramPacket(returnData, returnData.length, clientPacket.getAddress(), clientPacket.getPort()));
                }
            } catch (Exception e) {
                if (!(e instanceof SocketException)) {
                    e.printStackTrace();
                }
            }
        });
    }

    private Thread getHealthCheckNodeThread(boolean isCorrect) {
        return new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket(HEALTH_CHECK_PORT)) {
                DatagramPacket clientPacket = new DatagramPacket(new byte[Protocol.UDP.getMaxReceiveSize()],
                    Protocol.UDP.getMaxReceiveSize());
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