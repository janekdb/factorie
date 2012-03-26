/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie.util
import scala.util.Random

/** We are so desperate for efficient @specialized Seq[Double], that we created our own. 
    This could inherit from IndexedSeq[Double] but we would pass on significant risk of inefficiencies hidden to the user. */
// TODO Consider if we should move activeDomain from Tensor to here, so that there could be more efficient implementations centralized here.
//  But my current thinking is not to do this because many methods should have even more highly specialized implementations anyway. -akm
trait DoubleSeq {
  def apply(i:Int): Double
  def length: Int
  final def size = length // Just an alias
  def sampleIndex(normalizer:Double)(implicit r:Random): Int = {
    assert(normalizer > 0.0, "normalizer = "+normalizer)
    var b = 0.0; val s = r.nextDouble * normalizer; var i = 0
    while (b <= s && i < length) { assert(apply(i) >= 0.0); b += apply(i); i += 1 }
    assert(i > 0)
    i - 1
  }
  /** Careful, for many subclasses this is inefficient because it calls the method "sum" to get the normalizer. */
  def sampleIndex(implicit r:Random): Int = sampleIndex(sum)(r)
  def pr(i:Int, normalizer:Double): Double = apply(i) / normalizer
  def foreach(f:(Double)=>Unit): Unit = { var i = 0; while (i < length) { f(apply(i)); i += 1 } }
  def forElements(f:(Int,Double)=>Unit): Unit = { var i = 0; while (i < length) { f(i, apply(i)); i += 1 } }
  def forallElements(f:(Int,Double)=>Boolean): Boolean = { var i = 0; while (i < length) { if (!f(i, apply(i))) return false; i += 1 }; return true }
  def contains(d:Double): Boolean = { var i = length; while (i >= 0) { if (d == apply(i)) return true; i += 1 }; false }
  def forall(f:Double=>Boolean): Boolean = { var i = length; while (i >= 0) { if (!f(apply(i))) return false; i += 1 }; true }
  def foldLeft[B<:AnyRef](z:B)(f:(B,Double)=>B): B = throw new Error("Not yet implemented.")
  def indexOf(d:Double): Int = { var i = 0; while (i < length) { if (d == apply(i)) return i; i += 1 }; -1 }
  def max: Double = { var m = Double.NaN; var i = 0; while (i < length) { if (!(m >= apply(i))) m = apply(i); i += 1 }; m }
  def min: Double = { var m = Double.NaN; var i = 0; while (i < length) { if (!(m <= apply(i))) m = apply(i); i += 1 }; m }
  def sum: Double = { var s = 0.0; var i = 0; while (i < length) { s += apply(i); i += 1 }; s }
  def oneNorm: Double = { var result = 0.0; var i = 0; while (i < length) { result += math.abs(apply(i)); i += 1}; result }
  //def absNorm: Double = { var result = 0.0; var i = 0; while (i < length) { result += math.abs(apply(i)); i += 1}; result }
  def twoNormSquared: Double = { var result = 0.0; var i = 0; var v = 0.0; while (i < length) { v = apply(i); result += v * v; i += 1}; result }
  final def twoNorm: Double = math.sqrt(twoNormSquared)
  final def infinityNorm = max
  def different(t:DoubleSeq, threshold:Double): Boolean = { require(length == t.length); var i = 0; while (i < length) { if (math.abs(apply(i) - t(i)) > threshold) return true; i += 1}; return false }
  def dot(t:DoubleSeq): Double = { assert(length == t.length); var result = 0.0; var i = 0; while (i < length) { result += apply(i) * t(i); i += 1 }; result }
  def maxIndex: Int = { var i = 0; var j = 0; for (i <- 0 until length) if (apply(j) < apply(i)) j = i; j }
  def containsNaN: Boolean = { var i = length; while (i >= 0) { if (apply(i) != apply(i)) return true; i += 1 }; false }  // TODO Why wouldn't apply(i).isNaN compile?
  // def copy: DoubleSeq = DoubleSeq(this.toArray) // TODO We really should make a version of this that uses CanBuildFrom[] tricks to preserve return type
  /** Return the values as an Array[Double].  Guaranteed to be a copy, not just a pointer to an internal array that would change with changes to the DoubleSeq */
  def toArray: Array[Double] = { val a = new Array[Double](length); var i = 0; while (i < length) { a(i) = apply(i); i += 1 }; a }
  def asArray: Array[Double] = toArray // Can be overridden for further efficiency
  /** With uncopied contents */
  def asSeq: Seq[Double] = new IndexedSeq[Double] {
    def length = DoubleSeq.this.length
    def apply(i:Int): Double = DoubleSeq.this.apply(i)
  }
  /** With copied contents */
  def toSeq: Seq[Double] = new IndexedSeq[Double] {
    private val a = toArray
    def length = a.length
    def apply(i:Int): Double = a(i)
  }
}

object DoubleSeq {
  def apply(sd:Seq[Double]): DoubleSeq = new DoubleSeq {
    def length = sd.length
    def apply(i:Int) = sd(i)
  }
  def apply(a:Array[Double]): DoubleSeq = new MutableDoubleSeq {
    def length = a.length
    def apply(i:Int) = a(i)
    def +=(i:Int, v:Double) = a(i) += v
    def zero(): Unit = java.util.Arrays.fill(a, 0.0)
    def update(i:Int, v:Double) = a(i) = v
  }
}

trait IncrementableDoubleSeq extends DoubleSeq {
  def +=(i:Int, incr:Double): Unit
  def zero(): Unit
  def +=(d:Double): Unit = { var i = 0; while (i < length) { +=(i, d); i += 1 }}
  def +=(ds:DoubleSeq): Unit = { require(ds.length == length); var i = 0; while (i < length) { +=(i, ds(i)); i += 1 }}
  def +=(ds:DoubleSeq, factor:Double): Unit = { require(ds.length == length); var i = 0; while (i < length) { +=(i, factor*ds(i)); i += 1 }}
  def -=(i:Int, incr:Double): Unit = +=(i, -incr)
  def -=(d:Double): Unit = +=(-d)
  def -=(ds:DoubleSeq): Unit = +=(ds, -1.0)
}

trait MutableDoubleSeq extends IncrementableDoubleSeq {
  def update(i:Int, v:Double): Unit
  // Although the next two methods could be implemented here, they are usually implemented in a superclass that inherits from IncrementableDoubleSeq
  def +=(i:Int, incr:Double): Unit // = update(i, apply(i)+incr)
  def zero(): Unit // = this := 0.0 //{ var i = 0; while (i < length) { update(i, 0.0); i += 1 }}
  // Concrete methods, efficient for dense representations
  def substitute(oldValue:Double, newValue:Double): Unit = { var i = 0; while (i < length) { if (apply(i) == oldValue) update(i, newValue); i += 1 } }
  def :=(d:Double): Unit = { var i = 0; while (i < length) { update(i, d); i += 1 }}
  def :=(ds:DoubleSeq): Unit = { require(ds.length == length); var i = 0; while (i < length) { update(i, ds(i)); i += 1 }}
  def :=(a:Array[Double]): Unit = { require(a.length == length); var i = 0; while (i < length) { update(i, a(i)); i += 1 }}
  def *=(i:Int, incr:Double): Unit = update(i, apply(i)*incr)
  final def /=(i:Int, incr:Double): Unit = *=(i, 1.0/incr)
  def *=(d:Double): Unit = { var i = 0; while (i < length) { *=(i, d); i += 1 }}
  final def /=(d:Double): Unit = *=(1.0/d)
  def *=(ds:DoubleSeq): Unit = { require(ds.length == length); var i = 0; while (i < length) { *=(i, ds(i)); i += 1 }}
  def /=(ds:DoubleSeq): Unit = { require(ds.length == length); var i = 0; while (i < length) { /=(i, ds(i)); i += 1 }}
  def normalize(): Double = { val n = oneNorm; /=(n); n }
  def oneNormalize(): Double = normalize
  def twoNormalize(): Double = { val n = twoNorm; /=(n); n }
  def twoSquaredNormalize(): Double = { val n = twoNormSquared; /=(n); n }
  //def absNormalize(): Double = { val n = absNorm; /=(n); n }
  /** Exponentiate the elements of the array, and then normalize them to sum to one. */
  def expNormalize(): Double = {
    var max = Double.MinValue
    var i = 0
    while (i < length) { if (max < apply(i)) max = apply(i); i += 1 }
    var sum = 0.0
    i = 0
    while (i < length) {
      update(i, math.exp(apply(i) - max))
      sum += apply(i)
      i += 1
    }
    i = 0
    while (i < length) { apply(i) /= sum; i += 1 }
    sum
  }
  /** expNormalize, then put back into log-space. */
  def normalizeLogProb(): Double = {
    // normalizeLogProb: [log(a), log(b), log(c)] --> [log(a/Z), log(b/Z), log(c/Z)] where Z = a+b+c
    // expNormalize: [log(a), log(b), log(c)] --> [a/Z, b/Z, c/Z] where Z=a+b+c
    val n = expNormalize
    var i = 0; while (i < length) { update(i, math.log(apply(i))); i += 1 }
    n
  }
}

