package alexclin.patch.qfix.tool;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 反射工具
 */
public class ReflectUtil {
    public static Object getField(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        for (Class<?> clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                return getField(obj,clazz,fieldName);
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found in " + obj.getClass());
    }

    public static Object getField(Object obj, Class objClass, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = objClass.getDeclaredField(fieldName);
        if (!field.isAccessible()) field.setAccessible(true);
        return field.get(obj);
    }

    public static void setField(Object obj, String field, Object value)
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        for (Class<?> clazz = obj.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                setField(obj,clazz,field,value);
                return;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }
        throw new NoSuchFieldException("Field " + field + " not found in " + obj.getClass());
    }

    public static void setField(Object obj, Class clazz, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field localField = clazz.getDeclaredField(field);
        localField.setAccessible(true);
        localField.set(obj, value);
    }

    /**
     * Locates a given method anywhere in the class inheritance hierarchy.
     *
     * @param instance       an object to search the method into.
     * @param name           method name
     * @param parameterTypes method parameter types
     * @return a method object
     * @throws NoSuchMethodException if the method cannot be located
     */
    public static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);

                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }

                return method;
            } catch (NoSuchMethodException e) {
                // ignore and search next
            }
        }

        throw new NoSuchMethodException("Method "
                + name
                + " with parameters "
                + Arrays.asList(parameterTypes)
                + " not found in " + instance.getClass());
    }

    public static Object removeElementFromArray(Object array, int index) {
        Class<?> localClass = array.getClass().getComponentType();
        int len = Array.getLength(array);
        if (index < 0 || index >= len) {
            return array;
        }
        Object result = Array.newInstance(localClass, len - 1);
        int i = 0;
        for (int k = 0; k < len; ++k) {
            if (k != index) {
                Array.set(result, i++, Array.get(array, k));
            }
        }
        return result;
    }

    /**
     * Replace the value of a field containing a non null array, by a new array containing the
     * elements of the original array plus the elements of extraElements.
     *
     * @param instance      the instance whose field is to be modified.
     * @param fieldName     the field to modify.
     * @param extraElements elements to append at the end of the array.
     */
    public static void expandFieldArray(Object instance, String fieldName,
                                         Object[] extraElements, boolean isHotfix) throws NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        Object[] original = (Object[]) getField(instance,fieldName);
        Object[] combined = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length + extraElements.length);
        if (isHotfix) {
            System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
            System.arraycopy(original, 0, combined, extraElements.length, original.length);
        } else {
            System.arraycopy(original, 0, combined, 0, original.length);
            System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
        }
        setField(instance,fieldName,combined);
    }

    public static Object combineArray(Object firstArray, Object secondArray) {
        Class<?> localClass = firstArray.getClass().getComponentType();
        int firstArrayLength = Array.getLength(firstArray);
        int allLength = firstArrayLength + Array.getLength(secondArray);
        Object result = Array.newInstance(localClass, allLength);
        for (int k = 0; k < allLength; ++k) {
            if (k < firstArrayLength) {
                Array.set(result, k, Array.get(firstArray, k));
            } else {
                Array.set(result, k, Array.get(secondArray, k - firstArrayLength));
            }
        }
        return result;
    }

    public static Object appendArray(Object obj, Object obj2) {
        Class componentType = obj.getClass().getComponentType();
        int length = Array.getLength(obj);
        Object newInstance = Array.newInstance(componentType, length + 1);
        Array.set(newInstance, 0, obj2);
        for (int i = 1; i < length + 1; i++) {
            Array.set(newInstance, i, Array.get(obj, i - 1));
        }
        return newInstance;
    }
}
