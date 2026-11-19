package art.arcane.react.mod.cost;

/** Mixed into block entity and entity types so a tick finds its owner's bucket without a lookup. */
public interface CostBucketHolder {
    CostBucket react$costBucket();

    void react$costBucket(CostBucket bucket);
}
