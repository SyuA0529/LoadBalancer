package blog.syua.node.nodeimpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.forward.ForwardInfo;
import blog.syua.healthcheck.HealthCheckRequest;
import blog.syua.healthcheck.HealthCheckResponse;
import blog.syua.node.Node;
import blog.syua.node.Protocol;
import blog.syua.utils.NodeMessageUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpNode extends Node {

	@Value("${loadbalancer.healthcheck.timeout}")
	private final int HEALTH_CHECK_TIME_OUT = Integer.MAX_VALUE;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public TcpNode(String ipAddr, int port) throws UnknownHostException {
		super(ipAddr, port);
	}

	@Override
	public Protocol getProtocol() {
		return Protocol.TCP;
	}

	@Override
	public boolean isHealthy() {
		try (Socket socket = new Socket()) {
			socket.setSoTimeout(HEALTH_CHECK_TIME_OUT);
			socket.connect(new InetSocketAddress(getIpAddr(), getPort()));
			return getHealthCheckResult(socket);
		} catch (IOException exception) {
			log.error("TcpNode: Error occur in Health Check\n{}",
				Arrays.toString(exception.getStackTrace()));
		}
		return false;
	}

	@Override
	public ForwardInfo getForwardInfo() {
		return new ForwardInfo(Protocol.TCP, getPort());
	}

	private boolean getHealthCheckResult(Socket socket) throws IOException {
		try (InputStream inputStream = socket.getInputStream();
			 OutputStream outputStream = socket.getOutputStream()) {
			outputStream.write(objectMapper.writeValueAsBytes(HealthCheckRequest.getInstance()));
			outputStream.flush();

			byte[] bytes = inputStream.readAllBytes();
			HealthCheckResponse response = objectMapper.readValue(bytes, HealthCheckResponse.class);
			if (response.getAck().equals(HealthCheckResponse.SUCCESS_ACK)) {
				return true;
			}
		} catch (JsonParseException jsonParseException) {
			log.info("TcpNode: Json Parsing Error in Health Check\nNode Info: {} {} {}",
				getProtocol(), getIpAddr(), getPort());
		}
		return false;
	}

	public void forwardPacket(Socket clientSocket) {
		try (InputStream clientInputStream = clientSocket.getInputStream();
			 OutputStream clientOutputStream = clientSocket.getOutputStream()) {
			byte[] resultData = forwardDataToNode(clientInputStream.readAllBytes());
			clientOutputStream.write(resultData);
			clientOutputStream.flush();
			clientSocket.close();
		} catch (IOException e) {
			log.info("TcpNode: fail to forward packet\n{}", Arrays.toString(e.getStackTrace()));
			sendErrorMessage(clientSocket);
		}
	}

	private byte[] forwardDataToNode(byte[] forwardData) throws IOException {
		try (Socket nodeSocket = new Socket(getIpAddr(), getPort());
			 InputStream nodeInputStream = nodeSocket.getInputStream();
			 OutputStream nodeOutputStream = nodeSocket.getOutputStream()) {
			nodeOutputStream.write(forwardData);
			return nodeInputStream.readAllBytes();
		}
	}

	private void sendErrorMessage(Socket clientSocket) {
		try (OutputStream outputStream = clientSocket.getOutputStream()) {
			outputStream.write(NodeMessageUtil.getForwardErrorMessage());
			clientSocket.close();
		} catch (IOException exception) {
			log.error("TcpNode: Error Occur in sendErrorMessage\n{}", Arrays.toString(exception.getStackTrace()));
		}
	}

}
