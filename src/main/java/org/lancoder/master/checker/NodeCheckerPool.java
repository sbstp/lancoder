package org.lancoder.master.checker;

import org.lancoder.common.Node;
import org.lancoder.common.events.EventListener;
import org.lancoder.common.pool.Pool;
import org.lancoder.common.pool.PoolWorker;

/**
 * Loose pool implementation for node checking with custom listener interface
 *
 */
public class NodeCheckerPool extends Pool<Node> {

	private EventListener listener;

	public NodeCheckerPool(int ressourceLimit, EventListener listener) {
		super(ressourceLimit);
		this.listener = listener;
	}

	@Override
	protected PoolWorker<Node> getPoolWorkerInstance() {
		return new NodeChecker(listener);
	}
}
