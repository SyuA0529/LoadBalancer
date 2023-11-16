package blog.syua.control.dispatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.control.ControlType;
import blog.syua.control.dto.ControlFailResponse;
import blog.syua.control.dto.ControlRequest;
import blog.syua.control.dto.ControlResponse;
import blog.syua.control.requesthandler.ControlRequestHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ControlRequestDispatcherServer implements ControlRequestDispatcher {

	@Value("${loadbalancer.control.port:8080}")
	private final int controlRequestPort = 8080;

	@Value("${loadbalancer.control.timeout:5000}")
	private final int controlRequestTimeout = 5000;

	@Value("${loadbalancer.control.thread-pool-size:4}")
	private final int controlRequestThreadPoolSize = Runtime.getRuntime().availableProcessors();

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final EnumMap<ControlType, ControlRequestHandler> controlRequestHandlers;
	private final ExecutorService threadPool;

	public ControlRequestDispatcherServer() {
		controlRequestHandlers = new EnumMap<>(ControlType.class);
		threadPool = Executors.newFixedThreadPool(controlRequestThreadPoolSize);
	}

	@PostConstruct
	public void init() {
		startDispatcher();
	}

	@Override
	public void registerHandler(ControlRequestHandler controlRequestHandler) {
		if (controlRequestHandlers.containsValue(controlRequestHandler)) {
			log.error("ControlRequestHandler already exists {}", controlRequestHandler);
			throw new IllegalArgumentException("ControlRequestHandler already exists");
		}
		controlRequestHandlers.put(controlRequestHandler.getHandleType(), controlRequestHandler);
	}

	public void startDispatcher() {
		new Thread(() -> {
			try (ServerSocket serverSocket = new ServerSocket(controlRequestPort)) {
				Socket nodeSocket;
				while (Objects.nonNull(nodeSocket = serverSocket.accept())) {
					Socket finalNodeSocket = nodeSocket;
					threadPool.execute(() -> dispatcherControlRequest(finalNodeSocket));
				}
			} catch (Exception e) {
				log.error("Error occur in Dispatcher Control Request");
				e.printStackTrace();
			}
		}).start();
	}

	@Override
	public ControlRequestHandler getControlRequestHandler(ControlType controlType) {
		ControlRequestHandler controlRequestHandler = controlRequestHandlers.get(controlType);
		if (Objects.isNull(controlRequestHandler)) {
			throw new IllegalArgumentException("No ControlRequestHandler is available to process the request");
		}
		return controlRequestHandler;
	}

	@Override
	public void dispatcherControlRequest(Socket nodeSocket) {
		try (InputStream inputStream = nodeSocket.getInputStream();
			 OutputStream outputStream = nodeSocket.getOutputStream()) {
			nodeSocket.setSoTimeout(controlRequestTimeout);
			requestHandleControlRequest(inputStream, outputStream, nodeSocket.getInetAddress());
			nodeSocket.shutdownOutput();
		} catch (Exception exception) {
			log.error("Error occur in Dispatcher ControlRequest");
			exception.printStackTrace();
		}
	}

	private void requestHandleControlRequest(InputStream inputStream, OutputStream outputStream, InetAddress nodeIpAddr) throws
		IOException {
		byte[] requestData = null;
		ControlRequest controlRequest = null;
		try {
			requestData = inputStream.readAllBytes();
			controlRequest = objectMapper.readValue(requestData, ControlRequest.class);
			log.info("Receive Control Request - {}", controlRequest);
			ControlResponse response = getControlRequestHandler(controlRequest.getCmd())
				.handleRequest(controlRequest, nodeIpAddr);
			outputStream.write(objectMapper.writeValueAsBytes(response));
			outputStream.flush();
		} catch (IllegalArgumentException exception) {
			log.info("Cannot find ControlRequestHandler for Control Request - {}",
				controlRequest);
			outputStream.write(objectMapper.writeValueAsBytes(new ControlFailResponse(exception.getMessage())));
		} catch (JsonParseException exception) {
			log.info("Json Parsing Exception Error in Dispatcher Control Request - {}",
				new String(Objects.requireNonNull(requestData), StandardCharsets.UTF_8));
			outputStream.write(objectMapper.writeValueAsBytes(new ControlFailResponse("Json Parsing Exception")));
		}
	}

}
