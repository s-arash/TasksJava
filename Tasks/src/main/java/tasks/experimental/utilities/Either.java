package tasks.experimental.utilities;

/**
 * It's a sum type, meaning either case1 has a value, or case2.
 * @param <T1>
 * @param <T2>
 */
public class Either<T1, T2> {
    private T1 case1Item;
    private T2 case2Item;
    private boolean isCase1;

    public static <T1, T2> Either<T1, T2> createCase1(T1 case1Item, Class<T2> t2) {
        final Either<T1, T2> obj = new Either<>();
        obj.isCase1 = true;
        obj.case1Item = case1Item;
        return obj;
    }

    public static <T1, T2> Either<T1, T2> createCase2(Class<T1> t1, T2 case1Item) {
        final Either<T1, T2> obj = new Either<>();
        obj.isCase1 = false;
        obj.case2Item = case1Item;
        return obj;
    }

    public boolean isCase1() {
        return isCase1;
    }

    public boolean isCase2() {
        return !isCase1;
    }

    public T1 getCase1() {
        if (isCase1) {
            return case1Item;
        } else {
            throw new IllegalStateException("this Either has value for case 2");
        }
    }
    public T2 getCase2() {
        if (!isCase1) {
            return case2Item;
        } else {
            throw new IllegalStateException("this Either has value for case 1");
        }
    }

}
