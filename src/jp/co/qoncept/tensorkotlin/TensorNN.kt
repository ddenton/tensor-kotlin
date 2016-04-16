package jp.co.qoncept.tensorkotlin

val Tensor.softmax: Tensor
    get() {
        val exps = exp
        val sum = exps.elements.fold(0.0f) { r, x -> r + x }
        return exps / sum
    }

val Tensor.relu: Tensor
    get() {
        return Tensor(shape, this.elements.map { Math.max(it, 0.0f) })
    }

fun Tensor.maxPool(kernelSize: IntArray, strides: IntArray): Tensor {
    assert({ shape.dimensions.size == 3 }, { "`shape.dimensions.size` must be 3: ${shape.dimensions.size}" })
    assert({ kernelSize.size == 3 }, { "`kernelSize.size` must be 3: ${kernelSize.size}" })
    assert({ kernelSize[2] == 1 } , { "`kernelSize[2]` != 1 is not supported: ${ kernelSize[2] }" })
    assert({ strides.size == 3 }, { "`strides.size` must be 3: ${ strides.size }" })
    assert({ strides[2] == 1 } , { "`strides[2]` != 1 is not supported: ${ strides[2] }" })

    val numRows = shape.dimensions[0]
    val numCols = shape.dimensions[1]
    val numChannels = shape.dimensions[2]

    val filterHeight = kernelSize[0]
    val filterWidth = kernelSize[1]

    val minDy = -(filterHeight - 1) / 2
    val maxDy = minDy + filterHeight - 1
    val minDx = -(filterWidth - 1) / 2
    val maxDx = minDx + filterWidth - 1

    val rowStride = strides[0]
    val colStride = strides[1]

    val outRows = shape.dimensions[0] ceilDiv rowStride
    val outCols = shape.dimensions[1] ceilDiv colStride

    val elements = FloatArray(outCols * outRows * numChannels)

    var elementIndex = 0
    for (y in 0 until outRows) {
        for (x in 0 until outCols) {
            val inY = y * rowStride
            val inX = x * colStride

            val minY2 = Math.max(inY + minDy, 0)
            val maxY2 = Math.min(inY + maxDy, numRows - 1)
            val minX2 = Math.max(inX + minDx, 0)
            val maxX2 = Math.min(inX + maxDx, numCols - 1)

            val y2Offset = inY + minDy
            val x2Offset = inX + minDx

            for (c in 0 until numChannels) {
                var maxElement = Float.MIN_VALUE
                for (y2 in minY2..maxY2) {
                    for (x2 in minX2..maxX2) {
                        maxElement = Math.max(maxElement, this.elements[(y2 * numCols + x2) * numChannels + c])
                    }
                }
                elements[elementIndex++] = maxElement
            }
        }
    }

    return Tensor(Shape(outRows, outCols, numChannels), elements)
}

fun Tensor.conv2d(filter: Tensor, strides: IntArray): Tensor {
    val inChannels = filter.shape.dimensions[2]

    assert({ shape.dimensions.size == 3 }, { "`shape.dimensions.size` must be 3: ${shape.dimensions.size}" })
    assert({ filter.shape.dimensions.size == 4 }, { "`filter.shape.dimensions.size` must be 4: ${filter.shape.dimensions.size}" })
    assert({ strides.size == 3 }, { "`strides.size` must be 3: ${ strides.size }" })
    assert({ strides[2] == 1 } , { "`strides[2]` != 1 is not supported: ${ strides[2] }" })
    assert({ shape.dimensions[2] == inChannels }, { "The number of channels of this tensor and the filter are not compatible: ${shape.dimensions[2]} != ${inChannels}" })

    val numRows = shape.dimensions[0]
    val numCols = shape.dimensions[1]

    val filterHeight = filter.shape.dimensions[0]
    val filterWidth = filter.shape.dimensions[1]

    val minDy = -(filterHeight - 1) / 2
    val maxDy = minDy + filterHeight - 1
    val minDx = -(filterWidth - 1) / 2
    val maxDx = minDx + filterWidth - 1

    val rowStride = strides[0]
    val colStride = strides[1]

    val outRows = shape.dimensions[0] ceilDiv rowStride
    val outCols = shape.dimensions[1] ceilDiv colStride
    val outChannels = filter.shape.dimensions[3]

    val elements = FloatArray(outCols * outRows * outChannels)

    for (y in 0 until outRows) {
        for (x in 0 until outCols) {
            val inY = y * rowStride
            val inX = x * colStride

            val minY2 = Math.max(inY + minDy, 0)
            val maxY2 = Math.min(inY + maxDy, numRows - 1)
            val minX2 = Math.max(inX + minDx, 0)
            val maxX2 = Math.min(inX + maxDx, numCols - 1)

            val y2Offset = inY + minDy
            val x2Offset = inX + minDx

            for (y2 in minY2..maxY2) {
                for (x2 in minX2..maxX2) {
                    matmuladd(
                            inChannels, outChannels,
                            (y2 * numCols + x2) * inChannels, this.elements,
                            ((y2 - y2Offset) * filterWidth + (x2 - x2Offset)) * inChannels * outChannels, filter.elements,
                            (y * outCols + x) * outChannels, elements
                    )
                }
            }
        }
    }

    return Tensor(Shape(outRows, outCols, outChannels), elements)
}

internal fun matmuladd(inCols1Rows2: Int, outCols: Int, o1: Int, vec: FloatArray, o2: Int, mat: FloatArray, oo: Int, out: FloatArray) {
    for (i in 0 until inCols1Rows2) {
        var elementIndex = oo
        val left = vec[i + o1]
        for (c in 0 until outCols) {
            out[elementIndex] += left * mat[i * outCols + c + o2]
            elementIndex++
        }
    }
}
