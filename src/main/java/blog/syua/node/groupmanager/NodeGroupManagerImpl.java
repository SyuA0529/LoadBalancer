package blog.syua.node.groupmanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import blog.syua.node.group.ForwardInfo;
import blog.syua.node.group.NodeGroup;
import blog.syua.node.node.Node;
import blog.syua.node.node.Protocol;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NodeGroupManagerImpl implements NodeGroupManager {

	private final ConcurrentHashMap<ForwardInfo, NodeGroup> nodeGroups;
	private final List<NodeManagingListener> listeners;

	public NodeGroupManagerImpl() {
		nodeGroups = new ConcurrentHashMap<>();
		listeners = new ArrayList<>();
	}

	@Override
	public void registerNode(Protocol protocol, String ipAddr, int port) throws IOException {
		ForwardInfo forwardInfo = ForwardInfo.of(protocol, port);
		boolean isNewGroup = false;
		NodeGroup nodeGroup = findNodeGroup(forwardInfo);
		if (Objects.isNull(nodeGroup)) {
			isNewGroup = true;
			nodeGroup = NodeGroup.newInstance(protocol, port);
			nodeGroups.put(forwardInfo, nodeGroup);
		}
		log.info("NodeGroupManager: registerNode - {} {} {}", protocol.toString(), ipAddr, port);
		Node node = Node.newInstance(protocol, ipAddr, port);
		nodeGroup.registerNode(node);
		if (isNewGroup) {
			nodeGroup.startForwarding();
		}
		notifyRegisterNode(node);
	}

	@Override
	public void unRegisterNode(Protocol protocol, String ipAddr, int port) throws IOException {
		ForwardInfo forwardInfo = ForwardInfo.of(protocol, port);
		NodeGroup nodeGroup = findNodeGroup(forwardInfo);
		if (Objects.isNull(nodeGroup)) {
			log.info("NodeGroupManager: Attempted to delete a node that does not exist: - {} {} {}",
				protocol.toString(), ipAddr, port);
			throw new IllegalArgumentException("존재하지 않는 노드입니다");
		}
		log.info("NodeGroupManager: Unregister Node - {} {} {}", protocol, ipAddr, port);
		Node node = Node.newInstance(protocol, ipAddr, port);
		nodeGroup.unRegisterNode(node);
		if (nodeGroup.isEmpty()) {
			unRegisterNodeGroup(protocol, port, forwardInfo);
		}
		notifyUnRegisterNode(node);
	}

	private void unRegisterNodeGroup(Protocol protocol, int port, ForwardInfo forwardInfo) {
		nodeGroups.remove(forwardInfo);
		log.info("NodeGroupManager: Unregister NodeGroup - {} {}", protocol, port);
	}

	private NodeGroup findNodeGroup(ForwardInfo forwardInfo) {
		return nodeGroups.get(forwardInfo);
	}

	@Override
	public void registerListener(NodeManagingListener listener) {
		listeners.add(listener);
	}

	@Override
	public void notifyRegisterNode(Node node) {
		listeners.forEach(listener -> listener.onRegisterNode(node));
	}

	@Override
	public void notifyUnRegisterNode(Node node) {
		listeners.forEach(listener -> listener.onUnRegisterNode(node));
	}

}
