package blog.syua.node.node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.healthcheck.dto.HealthCheckRequest;
import blog.syua.healthcheck.dto.HealthCheckResponse;
import blog.syua.utils.NodeMessageUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpNode extends Node {

	@Value("${loadbalancer.udp.timeout:5000}")
	public final int timeout = 5000;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public UdpNode(InetAddress ipAddr, int port) {
		super(ipAddr, port);
	}

	@Override
	public Protocol getProtocol() {
		return Protocol.UDP;
	}

	public void forwardPacket(DatagramSocket loadBalancerSocket, DatagramPacket clientPacket) {
		try {
			DatagramSocket nodeSocket = new DatagramSocket();
			nodeSocket.setSoTimeout(timeout);
			InetAddress nodeIpAddr = getIpAddr();
			sendData(nodeSocket, nodeIpAddr, getPort(), clientPacket.getData());
			DatagramPacket resultPacket = receiveData(nodeSocket);
			sendData(loadBalancerSocket, clientPacket.getAddress(), clientPacket.getPort(),
				removeTrailingZeros(resultPacket.getData()));
		} catch (SocketTimeoutException timeoutException) {
			log.info("Socket Time Out - {Ip: {}, Port: {}}", clientPacket.getAddress(),
				clientPacket.getPort());
			sendErrorMessage(loadBalancerSocket, clientPacket);
		} catch (Exception exception) {
			exception.printStackTrace();
			sendErrorMessage(loadBalancerSocket, clientPacket);
		}
	}

	@Override
	public boolean isHealthy() {
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setSoTimeout(timeout);
			return getHealthCheckResponse(socket);
		} catch (Exception exception) {
			log.error("Error occur in Health Check");
			exception.printStackTrace();
		}
		return false;
	}

	private boolean getHealthCheckResponse(DatagramSocket socket) throws IOException {
		try {
			byte[] requestMessage = objectMapper.writeValueAsBytes(HealthCheckRequest.getInstance());
			sendData(socket, getIpAddr(), getPort(), requestMessage);
			DatagramPacket responsePacket = receiveData(socket);
			HealthCheckResponse response = objectMapper.readValue(removeTrailingZeros(responsePacket.getData()),
				HealthCheckResponse.class);
			if (response.getAck().equals(HealthCheckResponse.SUCCESS_ACK)) {
				return true;
			}
		} catch (JsonParseException jsonParseException) {
			log.info("Json Parsing Error in Health Check - Node Info: {} {} {}",
				getProtocol(), getIpAddr(), getPort());
		} catch (SocketTimeoutException exception) {
			log.info("Receive time out - Node Info: {} {} {}", getProtocol(), getIpAddr(), getPort());
		}
		return false;
	}

	private static DatagramPacket receiveData(DatagramSocket socket) throws IOException {
		DatagramPacket resultPacket = new DatagramPacket(new byte[Protocol.UDP.getMaxReceiveSize()],
			Protocol.UDP.getMaxReceiveSize());
		socket.receive(resultPacket);
		return resultPacket;
	}

	private void sendData(DatagramSocket socket, InetAddress ipAddr, int port, byte[] data) throws IOException {
		DatagramPacket packet = new DatagramPacket(data, data.length, ipAddr, port);
		socket.send(packet);
	}

	private void sendErrorMessage(DatagramSocket loadBalancerSocket, DatagramPacket clientPacket) {
		try {
			byte[] errorMessage = NodeMessageUtil.getForwardErrorMessage();
			DatagramPacket errorPacket = new DatagramPacket(errorMessage, errorMessage.length,
				clientPacket.getAddress(), clientPacket.getPort());
			loadBalancerSocket.send(errorPacket);
		} catch (IOException exception) {
			log.error("Error Occur in sendErrorMessage");
			exception.printStackTrace();
		}
	}

	private static byte[] removeTrailingZeros(byte[] data) {
		int endIndex = data.length - 1;
		while (endIndex >= 0 && data[endIndex] == 0) {
			endIndex--;
		}
		return Arrays.copyOf(data, endIndex + 1);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;
		if (object == null || getClass() != object.getClass())
			return false;
		if (!super.equals(object))
			return false;
		UdpNode udpNode = (UdpNode)object;
		return Objects.equals(getProtocol(), udpNode.getProtocol());
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), getProtocol());
	}

}
