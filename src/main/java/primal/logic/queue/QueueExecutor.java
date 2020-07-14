package primal.logic.queue;

public interface QueueExecutor<T>
{
	public void queue(Queue<T> t);

	public Queue<T> getQueue();

	public void start();

	public void stop();

	public void doUpdate();

	public void async(boolean async);

	public void interval(int ticks);
}
