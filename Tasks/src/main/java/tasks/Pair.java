package tasks;

/**
 * It is a tuple type of two values
 */
public class Pair<T1,T2> {
    public final T1 item1;
    public final T2 item2;
    public Pair(T1 item1, T2 item2){
        this.item1 = item1;
        this.item2 = item2;
    }

    @Override
    public boolean equals(Object o) {
        return o!= null && o instanceof Pair && objectsEqual(this.item1, ((Pair) o).item1) && objectsEqual(item2,((Pair) o).item2);
    }

    @Override
    public String toString() {
        return String.format("(%s,%s)",String.valueOf(item1), String.valueOf(item2));
    }

    private static boolean objectsEqual(Object o1, Object o2){
        return o1 == null ? o2==null : o1.equals(o2);
    }
}
