package com.ixnah.mc.multiprotocol.util;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionHelper {
    private static Field modifiersField;

    private ReflectionHelper() {
    }

    static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    public static Lookup getImplLookup() throws NoSuchFieldException, IllegalAccessException {
        Field implLookupField = Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Unsafe unsafe = getUnsafe();
        return (Lookup) unsafe.getObject(unsafe.staticFieldBase(implLookupField), unsafe.staticFieldOffset(implLookupField));
    }

    // https://github.com/apache/hbase/blob/master/hbase-common/src/main/java/org/apache/hadoop/hbase/util/ReflectionUtils.java#L232
    public static Field getModifiersField() throws NoSuchFieldException {
        if (modifiersField == null) {
            try {
                modifiersField = Field.class.getDeclaredField("modifiers");
            } catch (NoSuchFieldException e) {
                try {
                    Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                    boolean accessibleBeforeSet = getDeclaredFields0.isAccessible();
                    getDeclaredFields0.setAccessible(true);
                    Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
                    getDeclaredFields0.setAccessible(accessibleBeforeSet);
                    for (Field field : fields) {
                        if ("modifiers".equals(field.getName())) {
                            modifiersField = field;
                            break;
                        }
                    }
                    if (modifiersField == null) {
                        throw e;
                    }
                } catch (Throwable throwable) {
                    e.addSuppressed(throwable);
                    throw e;
                }
            }
        }
        return modifiersField;
    }

    public static void setModifiers(Field field, int fieldModifiers) throws IllegalAccessException {
        try {
            Field modifiersField = getModifiersField();
            boolean accessibleBeforeSet = modifiersField.isAccessible();
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, fieldModifiers);
            modifiersField.setAccessible(accessibleBeforeSet);
        } catch (Throwable e) {
            try {
                MethodHandle setterHandle = getImplLookup().findSetter(Field.class, "modifiers", int.class);
                setterHandle.invoke(field, fieldModifiers);
            } catch (Throwable ex) {
                throw new RuntimeException("Failed to find the \"modifiers\" field. Please use HotSpot and add the following JVM parameters and try again!\n\t--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED", ex);
            }
        }
    }
}
