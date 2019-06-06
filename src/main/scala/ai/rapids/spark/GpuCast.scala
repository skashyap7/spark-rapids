/*
 * Copyright (c) 2019, NVIDIA CORPORATION.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.rapids.spark

import ai.rapids.cudf.{DType, TimeUnit}

import org.apache.spark.sql.catalyst.expressions.{Cast, Expression}
import org.apache.spark.sql.types._
import org.apache.spark.sql.vectorized.{ColumnarBatch, ColumnVector}

object GpuCast {
  /**
   * Returns true iff we can cast `from` to `to` using the GPU.
   */
  def canCast(from: DataType, to: DataType): Boolean =
    (from, to) match {
    case (fromType, toType) if fromType == toType => true

    case (DateType, BooleanType) => true
    case (TimestampType, BooleanType) => true
    case (_: NumericType, BooleanType) => true

    case (BooleanType, TimestampType) => true
    case (DateType, TimestampType) => true
    case (_: NumericType, TimestampType) => true

    case (TimestampType, DateType) => true

    // TODO getting errors on going to string types
    //case (IntegerType, StringType) => true
    //case (LongType, StringType) => true
    //case (FloatType, StringType) => true
    //case (DoubleType, StringType) => true

    // case (StringType, IntegerType) => true
    // case (StringType, LongType) => true
    // case (StringType, FloatType) => true
    // case (StringType, DoubleType) => true
    case (BooleanType, _: NumericType) => true
    case (DateType, _: NumericType) => true
    case (TimestampType, _: NumericType) => true
    case (_: NumericType, _: NumericType) => true

    case _ => false
  }
}

/**
 * Casts using the GPU
 */
class GpuCast(child: Expression, dataType: DataType, timeZoneId: Option[String] = None)
  extends Cast(child, dataType, timeZoneId) {

  override def supportsColumnar(): Boolean = child.supportsColumnar

  def cudfType(): DType = GpuColumnVector.getRapidsType(dataType)
  def cudfTimeUnit(): TimeUnit = GpuColumnVector.getTimeUnits(dataType)

  override def columnarEval(batch: ColumnarBatch): Any = {
    var ret: Any = null
    var input: Any = null
    try {
      input = child.columnarEval(batch)
      if (input.isInstanceOf[GpuColumnVector]) {
        ret = GpuColumnVector.from(
          input.asInstanceOf[GpuColumnVector].getBase.castTo(cudfType, cudfTimeUnit))
      } else {
        ret = nullSafeEval(input)
      }
    } finally {
      if (input != null && input.isInstanceOf[ColumnVector]) {
        input.asInstanceOf[ColumnVector].close()
      }
    }
    ret
  }

  override def equals(other: Any): Boolean = {
    if (!super.equals(other)) {
      return false
    }
    return other.isInstanceOf[GpuCast]
  }
}
