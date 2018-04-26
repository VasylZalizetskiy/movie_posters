package utils

import com.twitter.chill.{KryoInstantiator, KryoPool, ScalaKryoInstantiator}

object KryoSerializer {

  val pool = ScalaKryoInstantiator.defaultPool

  def toBytes[T](t: T): Array[Byte] = pool.toBytesWithClass(t)
  def fromBytes[T](bytes: Array[Byte]): T = pool.fromBytes(bytes).asInstanceOf[T]

  def fromKryoInstantiator(k: KryoInstantiator) = KryoPool.withByteArrayOutputStream(1, k)

}
