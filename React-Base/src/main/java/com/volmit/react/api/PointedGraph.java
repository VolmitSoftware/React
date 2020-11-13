package com.volmit.react.api;

public class PointedGraph
{
	private static int iid = 0;
	private IGraph graph;
	private GraphSize size;
	private int id;

	public PointedGraph(IGraph graph, GraphSize size)
	{
		id = iid++;
		this.graph = graph;
		this.size = size;
	}

	public IGraph getGraph()
	{
		return graph;
	}

	public void setGraph(IGraph graph)
	{
		this.graph = graph;
	}

	public GraphSize getSize()
	{
		return size;
	}

	public void setSize(GraphSize size)
	{
		this.size = size;
	}

	public int getId()
	{
		return id;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((graph == null) ? 0 : graph.hashCode());
		result = prime * result + id;
		result = prime * result + ((size == null) ? 0 : size.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		PointedGraph other = (PointedGraph) obj;
		if(graph == null)
		{
			if(other.graph != null)
				return false;
		}
		else if(!graph.equals(other.graph))
			return false;
		if(id != other.id)
			return false;
		if(size != other.size)
			return false;
		return true;
	}
}
