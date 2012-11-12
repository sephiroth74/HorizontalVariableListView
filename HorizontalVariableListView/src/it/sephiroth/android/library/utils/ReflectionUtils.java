package it.sephiroth.android.library.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReflectionUtils {

	public static final class ReflectionException extends Exception {

		private static final long serialVersionUID = 1L;

		public ReflectionException( Throwable e ) {
			super( e );
		}

		public ReflectionException( String string ) {
			super( string );
		}
	}

	@SuppressWarnings("unchecked")
	/**
	 * Creates a new instance of the passed class
	 * @param className
	 * 	- the full qualified name of the class to be initialized
	 * @param paramTypes
	 * 	- optional parameter types for the constructor
	 * @param paramValues
	 * 	- optional parameter values for the constructor
	 * @return
	 * 	- the new instance
	 * 
	 * @throws ReflectionException
	 */
	public static <T> T newInstance( String className, Class<?>[] paramTypes, Object... paramValues ) throws ReflectionException {

		if ( paramTypes.length != paramValues.length ) {
			throw new ReflectionException( "parameterTypes and parameterValues must have the same length" );
		}

		Class<?> clazz;
		try {
			clazz = Class.forName( className );
		} catch ( ClassNotFoundException e ) {
			throw new ReflectionException( e );
		} catch ( ExceptionInInitializerError e ) {
			throw new ReflectionException( e );
		}

		Constructor<?> ctor;
		try {
			ctor = clazz.getConstructor( paramTypes );
		} catch ( SecurityException e ) {
			throw new ReflectionException( e );
		} catch ( NoSuchMethodException e ) {
			throw new ReflectionException( e );
		}

		try {
			return (T) ctor.newInstance( paramValues );
		} catch ( IllegalArgumentException e ) {
			throw new ReflectionException( e );
		} catch ( InstantiationException e ) {
			throw new ReflectionException( e );
		} catch ( IllegalAccessException e ) {
			throw new ReflectionException( e );
		} catch ( InvocationTargetException e ) {
			throw new ReflectionException( e );
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T helper( Object target, final String className, final String methodName, final Class<?>[] argTypes,
			final Object[] args ) throws ReflectionException {
		try {
			Class<?> cls;
			if ( target != null ) {
				cls = target.getClass();
			} else {
				cls = Class.forName( className );
				target = cls;
			}

			Method method;

			if ( null != argTypes ) {
				method = getMethod( cls, methodName, argTypes );
				return (T) method.invoke( target, args );
			} else {
				method = getMethod( cls, methodName );
				return (T) method.invoke( target );
			}

		} catch ( final IllegalAccessException e ) {
			throw new ReflectionException( e );
		} catch ( final InvocationTargetException e ) {
			throw new ReflectionException( e );
		} catch ( final ClassNotFoundException e ) {
			throw new ReflectionException( e );
		} catch ( NullPointerException e ) {
			throw new ReflectionException( e );
		}
	}

	/**
	 * Use reflection to invoke a static method for a class object and method name
	 * 
	 * @param <T>
	 *           Type that the method should return
	 * @param className
	 *           Name of the class on which to invoke {@code methodName}. Cannot be null.
	 * @param methodName
	 *           Name of the method to invoke. Cannot be null.
	 * @param types
	 *           explicit types for the objects. This is useful if the types are primitives, rather than objects.
	 * @param args
	 *           arguments for the method. May be null if the method takes no arguments.
	 * @return The result of invoking the named method on the given class for the args
	 * @throws ReflectionException
	 * @throws RuntimeException
	 *            if the class or method doesn't exist
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invokeStaticMethod( final String className, final String methodName, final Class<?>[] types,
			final Object... args ) throws ReflectionException {
		return (T) helper( null, className, methodName, types, args );
	}

	/**
	 * Try to invoke a method of the passed object. It does not throw any exception if the methodName does not exist on the declared
	 * class object
	 * 
	 * @param object
	 * @param methodName
	 * @param parameterTypes
	 *           Parameter types of the invoking method
	 * @param parameterValues
	 *           Parameter values to be passed to the method being invoked
	 * @return
	 * @throws ReflectionException
	 * @throws IllegalArgumentException
	 *            if parameterTypes and parameterValues have different length
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invokeMethod( final Object object, final String methodName, final Class<?>[] parameterTypes,
			Object... parameterValues ) throws ReflectionException {
		return (T) helper( object, null, methodName, parameterTypes, parameterValues );
	}

	/**
	 * @see ReflectionUtils#invokeMethod(Object, String, Class[], Object...)
	 * @param object
	 * @param methodName
	 * @return
	 * @throws ReflectionException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invokeMethod( Object object, String methodName ) throws ReflectionException {
		return (T) helper( object, null, methodName, null, null );
	}

	public static List<Method> getStaticMethods( Class<?> clazz ) {
		List<Method> methods = new ArrayList<Method>();
		for ( Method method : clazz.getMethods() ) {
			if ( Modifier.isStatic( method.getModifiers() ) ) {
				methods.add( method );
			}
		}
		return Collections.unmodifiableList( methods );
	}

	public static Method getMethod( Class<?> classObject, String methodName, Class<?>[] paramTypes ) {
		try {
			return classObject.getMethod( methodName, paramTypes );
		} catch ( NoSuchMethodException e ) {}
		return null;
	}

	public static Method getMethod( Class<?> classObject, String methodName ) {
		try {
			return classObject.getMethod( methodName );
		} catch ( NoSuchMethodException e ) {}
		return null;
	}
}
