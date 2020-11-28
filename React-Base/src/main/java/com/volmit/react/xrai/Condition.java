package com.volmit.react.xrai;

import com.volmit.react.api.SampledType;

public class Condition
{
	private SampledType sampler;
	private ConditionOp operation;
	private double value;

	public Condition(SampledType sampler, ConditionOp operation, double value)
	{
		this.sampler = sampler;
		this.operation = operation;
		this.value = value;
	}

	public Condition(String op)
	{
		String c = "";

		if(op.contains(">"))
		{
			operation = ConditionOp.GREATER;
			c = ">";
		}

		if(op.contains("<"))
		{
			operation = ConditionOp.LESS;
			c = "<";
		}

		if(!c.isEmpty())
		{
			String sid = op.split(c)[0];
			value = Double.valueOf(op.split(c)[1]);
			sampler = SampledType.valueOf(sid.toUpperCase());
		}
	}

	public boolean isMet()
	{
		if(operation.equals(ConditionOp.GREATER))
		{
			return sampler.get().getValue() > value;
		}

		if(operation.equals(ConditionOp.LESS))
		{
			return sampler.get().getValue() < value;
		}

		return false;
	}

	@Override
	public String toString()
	{
		return sampler.name().toLowerCase() + (operation.equals(ConditionOp.GREATER) ? ">" : "<") + value;
	}

	public SampledType getSampler()
	{
		return sampler;
	}

	public void setSampler(SampledType sampler)
	{
		this.sampler = sampler;
	}

	public ConditionOp getOperation()
	{
		return operation;
	}

	public void setOperation(ConditionOp operation)
	{
		this.operation = operation;
	}

	public double getValue()
	{
		return value;
	}

	public void setValue(double value)
	{
		this.value = value;
	}
}
