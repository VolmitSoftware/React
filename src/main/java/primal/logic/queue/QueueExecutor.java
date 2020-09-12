package primal.logic.queue;

public interface QueueExecutor<T>
{
	void queue(Queue<T> t);

	Queue<T> getQueue();

	void start();

	void stop();

	void doUpdate();

	void async(boolean async);

	void interval(int ticks);
}
