package blog.syua.control.requesthandler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import blog.syua.control.ControlType;
import blog.syua.control.dispatcher.ControlRequestDispatcher;
import blog.syua.control.dto.ControlFailResponse;
import blog.syua.control.dto.ControlRequest;
import blog.syua.control.dto.ControlResponse;
import blog.syua.control.dto.ControlSuccessResponse;
import blog.syua.node.groupmanager.NodeGroupManager;
import blog.syua.node.node.Protocol;

@DisplayName("UnRegisterControlRequestHandler 테스트")
class UnRegisterControlRequestHandlerTest {

	private UnRegisterControlRequestHandler unRegisterControlRequestHandler;
	private NodeGroupManager mockedNodeGroupManager;

	@BeforeEach
	void beforeEach() {
		mockedNodeGroupManager = mock(NodeGroupManager.class);
		unRegisterControlRequestHandler = new UnRegisterControlRequestHandler(mock(ControlRequestDispatcher.class), mockedNodeGroupManager);
	}

	@Nested
	@DisplayName("Method: handleRequest")
	class MethodHandleRequest {
		@Test
		@DisplayName("UNREGISTER 요청이 아닌 경우 ControlFailResponse를 반환한다")
		void returnControlFailResponseWhenRequestIsNotRegister() throws UnknownHostException {
			//given
			ControlRequest wrongControlRequest = new ControlRequest(ControlType.REGISTER, Protocol.TCP, 0);

			//when
			ControlResponse response = unRegisterControlRequestHandler.handleRequest(wrongControlRequest, InetAddress.getLocalHost());

			//then
			assertThat(response).isInstanceOf(ControlFailResponse.class);
		}

		@Test
		@DisplayName("UNREGISTER 요청에 성공한 경우 ControlSuccessResponse를 반환한다")
		void returnControlSuccessResponse() throws IOException {
			//given
			ControlRequest controlRequest = new ControlRequest(ControlType.UNREGISTER, Protocol.TCP, 0);
			doNothing().when(mockedNodeGroupManager).unRegisterNode(same(Protocol.TCP), any(InetAddress.class), anyInt());

			//when
			ControlResponse response = unRegisterControlRequestHandler.handleRequest(controlRequest,
				InetAddress.getLocalHost());

			//then
			assertThat(response).isInstanceOf(ControlSuccessResponse.class);
		}

		@Test
		@DisplayName("UNREGISTER 요청에 실패한 경우 ControlFailResponse를 반환한다")
		void returnControlFailResponse() throws IOException {
			//given
			ControlRequest controlRequest = new ControlRequest(ControlType.UNREGISTER, Protocol.TCP, 0);
			doThrow(new IOException()).when(mockedNodeGroupManager).unRegisterNode(same(Protocol.TCP), any(InetAddress.class), anyInt());

			//when
			ControlResponse response = unRegisterControlRequestHandler.handleRequest(controlRequest,
				InetAddress.getLocalHost());

			//then
			assertThat(response).isInstanceOf(ControlFailResponse.class);
		}
	}

}