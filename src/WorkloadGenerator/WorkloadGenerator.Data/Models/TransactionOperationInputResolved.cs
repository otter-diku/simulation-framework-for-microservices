namespace WorkloadGenerator.Data.Models;

public class TransactionOperationInputResolved<T> : TransactionOperationInputBase
{
    public T? Payload { get; set; }


    protected bool Equals(TransactionOperationInputResolved<T> other)
    {
        return base.Equals(other) && EqualityComparer<T?>.Default.Equals(Payload, other.Payload);
    }

    public override bool Equals(object? obj)
    {
        if (ReferenceEquals(null, obj)) return false;
        if (ReferenceEquals(this, obj)) return true;
        if (obj.GetType() != this.GetType()) return false;
        return base.Equals(obj) && Equals((TransactionOperationInputResolved<T>)obj);
    }

    public override int GetHashCode()
    {
        return EqualityComparer<T?>.Default.GetHashCode(Payload);
    }
}
