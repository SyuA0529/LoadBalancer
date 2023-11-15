package blog.syua.control.dispatcher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import blog.syua.control.ControlType;
import blog.syua.control.dto.ControlFailResponse;
import blog.syua.control.dto.ControlRequest;
import blog.syua.control.dto.ControlSuccessResponse;
import blog.syua.control.requesthandler.ControlRequestHandler;
import blog.syua.control.requesthandler.RegisterControlRequestHandler;
import blog.syua.control.requesthandler.UnRegisterControlRequestHandler;
import blog.syua.node.node.Protocol;

@DisplayName("ControlRequestDispatcher 테스트")
class ControlRequestDispatcherServerTest {

	private static final int MAX_DATA_SIZE = 8192;
	
	private ControlRequestDispatcher dispatcher;
	private ControlRequestHandler mockedRegistrationHandler;
	private ControlRequestHandler mockedUnRegistrationHandler;
	private Socket mockedNodeSocket;
	private byte[] requestData = null;
	private final ByteArrayOutputStream nodeSocketOutputStream = new ByteArrayOutputStream(MAX_DATA_SIZE);
	private SoftAssertions softAssertions;

	@BeforeEach
	void beforeEach() throws IOException {
		dispatcher = new ControlRequestDispatcherServer();
		initMockedRegisterControlRequestHandlers();
		initMockedClientSocket();
		softAssertions = new SoftAssertions();
	}

	private void initMockedRegisterControlRequestHandlers() {
		mockedRegistrationHandler = mock(RegisterControlRequestHandler.class);
		when(mockedRegistrationHandler.getHandleType()).thenReturn(ControlType.REGISTER);
		mockedUnRegistrationHandler = mock(UnRegisterControlRequestHandler.class);
		when(mockedUnRegistrationHandler.getHandleType()).thenReturn(ControlType.UNREGISTER);
		dispatcher.registerHandler(mockedRegistrationHandler);
		dispatcher.registerHandler(mockedUnRegistrationHandler);
	}

	private void initMockedClientSocket() throws IOException {
		mockedNodeSocket = mock(Socket.class);
		when(mockedNodeSocket.getOutputStream()).thenReturn(nodeSocketOutputStream);
		when(mockedNodeSocket.getInetAddress()).thenReturn(InetAddress.getLoopbackAddress());
		when(mockedNodeSocket.getPort()).thenReturn(50000);
	}

	@Nested
	@DisplayName("Method: getControlRequestHandler")
	class MethodGetControlRequestHandler {
		@Test
		@DisplayName("ControlType에 해당하는 ControlRequestHandler를 반환한다")
		void returnControlRequestHandler() {
			//given
			//when
			ControlRequestHandler registerHandler = dispatcher.getControlRequestHandler(ControlType.REGISTER);
			ControlRequestHandler unRegisterHandler = dispatcher.getControlRequestHandler(ControlType.UNREGISTER);

			//then
			softAssertions.assertThat(registerHandler).isInstanceOf(RegisterControlRequestHandler.class);
			softAssertions.assertThat(unRegisterHandler).isInstanceOf(UnRegisterControlRequestHandler.class);
			softAssertions.assertAll();
		}

		@Test
		@DisplayName("ControlType에 해당하는 ControlRequestHandler가 없는 경우 IllegalArgumentException 예외를 발생시킨다")
		void throwIllegalArgumentException() {
			//given
			//when
			//then
			assertThatThrownBy(() -> dispatcher.getControlRequestHandler(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("No ControlRequestHandler is available to process the request");
		}
	}

	@Nested
	@DisplayName("Method: dispatcherControlRequest")
	class MethodGetControlRequestDispatcherThread {
		private ObjectMapper objectMapper = new ObjectMapper();

		@Test
		@DisplayName("ControlRequest의 성공에 해당하는 ControlResponse를 반환한다")
		void returnSuccessControlResponse() throws IOException {
		    //given
			ControlRequest controlRequest = new ControlRequest(ControlType.REGISTER, Protocol.TCP, 80);
			requestData = objectMapper.writeValueAsBytes(controlRequest);
			when(mockedNodeSocket.getInputStream()).thenReturn(new ByteArrayInputStream(requestData));
			when(mockedRegistrationHandler.handleRequest(any(ControlRequest.class), any(InetAddress.class)))
				.thenReturn(new ControlSuccessResponse());

			//when
			dispatcher.dispatcherControlRequest(mockedNodeSocket);

		    //then
			ControlSuccessResponse response = objectMapper.readValue(nodeSocketOutputStream.toByteArray(),
				ControlSuccessResponse.class);
			assertThat(response.getAck()).isEqualTo(ControlSuccessResponse.SUCCESS_ACK);
		}

		@Test
		@DisplayName("받은 요청의 Json 파싱에 실패한 경우 노드에게 Json Parsing Exception 메세지를 갖는 ControlRequestFail 메세지를 보낸다")
		void writeLogWhenJsonParsingFailed() throws IOException {
			//given
			requestData = "Wrong Request Data".getBytes(StandardCharsets.UTF_8);
			when(mockedNodeSocket.getInputStream()).thenReturn(new ByteArrayInputStream(requestData));
			when(mockedRegistrationHandler.handleRequest(any(ControlRequest.class), any(InetAddress.class)))
				.thenReturn(new ControlSuccessResponse());

			//when
			dispatcher.dispatcherControlRequest(mockedNodeSocket);

			//then
			ControlFailResponse response = objectMapper.readValue(nodeSocketOutputStream.toByteArray(),
				ControlFailResponse.class);
			softAssertions.assertThat(response.getAck()).isEqualTo(ControlFailResponse.FAIL_ACK);
			softAssertions.assertThat(response.getMsg()).contains("Json Parsing Exception");
			softAssertions.assertAll();
		}

		@Test
		@DisplayName("받은 요청을 처리할 수 있는 ControlRequestHandler를 찾지 못한 경우 노드에게 No ControlRequestHandler is available 메세지를 갖는 ControlRequestFail 메세지를 보낸다")
		void writeLogWhenCannotFindControlRequestHandler() throws IOException {
			//given
			ControlRequest controlRequest = new ControlRequest(ControlType.REGISTER, null, 80);
			requestData = objectMapper.writeValueAsBytes(controlRequest);
			when(mockedNodeSocket.getInputStream()).thenReturn(new ByteArrayInputStream(requestData));
			when(mockedRegistrationHandler.handleRequest(any(ControlRequest.class), any(InetAddress.class)))
				.thenReturn(new ControlFailResponse("No ControlRequestHandler is available to process the request"));

			//when
			dispatcher.dispatcherControlRequest(mockedNodeSocket);

			//then
			ControlFailResponse response = objectMapper.readValue(nodeSocketOutputStream.toByteArray(),
				ControlFailResponse.class);
			softAssertions.assertThat(response.getAck()).isEqualTo(ControlFailResponse.FAIL_ACK);
			softAssertions.assertThat(response.getMsg()).contains("No ControlRequestHandler is available");
			softAssertions.assertAll();
		}
	}

}
