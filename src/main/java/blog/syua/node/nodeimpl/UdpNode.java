package blog.syua.node.nodeimpl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.forward.ForwardInfo;
import blog.syua.healthcheck.HealthCheckRequest;
import blog.syua.healthcheck.HealthCheckResponse;
import blog.syua.node.Node;
import blog.syua.node.Protocol;
import blog.syua.utils.NodeMessageUtil;

public class UdpNode extends Node {

	private static final Logger logger = LoggerFactory.getLogger(UdpNode.class);
	public static final int TIME_OUT = 3000;
	private static final int HEALTH_CHECK_TIME_OUT = 5000;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public UdpNode(String ipAddr, int port) throws UnknownHostException {
		super(ipAddr, port);
	}

	@Override
	public Protocol getProtocol() {
		return Protocol.UDP;
	}

	public void forwardPacket(DatagramSocket loadBalancerSocket, DatagramPacket clientPacket) {
		try (DatagramSocket nodeSocket = new DatagramSocket()) {
			InetAddress nodeIpAddr = getIpAddr();
			nodeSocket.setSoTimeout(TIME_OUT);
			sendData(nodeSocket, nodeIpAddr, getPort(), clientPacket.getData());
			DatagramPacket resultPacket = receiveData(nodeSocket);
			sendData(loadBalancerSocket, clientPacket.getAddress(), clientPacket.getPort(),
				removeTrailingZeros(resultPacket.getData()));
		} catch (IOException exception) {
			logger.info(Arrays.toString(exception.getStackTrace()));
			sendErrorMessage(loadBalancerSocket);
		}
	}

	@Override
	public boolean isHealthy() {
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setSoTimeout(HEALTH_CHECK_TIME_OUT);
			return getHealthCheckResponse(socket);
		} catch (IOException exception) {
			logger.error("UdpNode: Error occur in Health Check\n{}",
				Arrays.toString(exception.getStackTrace()));
		}
		return false;
	}

	@Override
	public ForwardInfo getForwardInfo() {
		return new ForwardInfo(Protocol.UDP, getPort());
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
			logger.info("TcpNode: Json Parsing Error in Health Check\nNode Info: {} {} {}",
				getProtocol(), getIpAddr(), getPort());
		}
		return false;
	}

	private static DatagramPacket receiveData(DatagramSocket socket) throws IOException {
		DatagramPacket resultPacket = new DatagramPacket(new byte[socket.getReceiveBufferSize()],
			socket.getReceiveBufferSize());
		socket.receive(resultPacket);
		return resultPacket;
	}

	private void sendData(DatagramSocket socket, InetAddress ipAddr, int port, byte[] data) throws IOException {
		DatagramPacket packet = new DatagramPacket(data, data.length, ipAddr, port);
		socket.send(packet);
	}

	private void sendErrorMessage(DatagramSocket loadBalancerSocket) {
		try {
			byte[] errorMessage = NodeMessageUtil.getForwardErrorMessage();
			DatagramPacket errorPacket = new DatagramPacket(errorMessage, errorMessage.length);
			loadBalancerSocket.send(errorPacket);
		} catch (IOException exception) {
			logger.error("TcpNode: Error Occur in sendErrorMessage\n{}", Arrays.toString(exception.getStackTrace()));
		}
	}

	private static byte[] removeTrailingZeros(byte[] data) {
		int endIndex = data.length - 1;
		while (endIndex >= 0 && data[endIndex] == 0) {
			endIndex--;
		}
		return Arrays.copyOf(data, endIndex + 1);
	}

}
