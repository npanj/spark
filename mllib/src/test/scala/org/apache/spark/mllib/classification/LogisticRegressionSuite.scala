/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.classification

import scala.util.Random
import scala.collection.JavaConversions._

import org.scalatest.FunSuite
import org.scalatest.Matchers

import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression._
import org.apache.spark.mllib.util.{LocalClusterSparkContext, LocalSparkContext}
import org.apache.spark.mllib.util.TestingUtils._

object LogisticRegressionSuite {

  def generateLogisticInputAsList(
    offset: Double,
    scale: Double,
    nPoints: Int,
    seed: Int): java.util.List[LabeledPoint] = {
    seqAsJavaList(generateLogisticInput(offset, scale, nPoints, seed))
  }

  // Generate input of the form Y = logistic(offset + scale*X)
  def generateLogisticInput(
      offset: Double,
      scale: Double,
      nPoints: Int,
      seed: Int): Seq[LabeledPoint]  = {
    val rnd = new Random(seed)
    val x1 = Array.fill[Double](nPoints)(rnd.nextGaussian())

    val y = (0 until nPoints).map { i =>
      val p = 1.0 / (1.0 + math.exp(-(offset + scale * x1(i))))
      if (rnd.nextDouble() < p) 1.0 else 0.0
    }

    val testData = (0 until nPoints).map(i => LabeledPoint(y(i), Vectors.dense(Array(x1(i)))))
    testData
  }
}

class LogisticRegressionSuite extends FunSuite with LocalSparkContext with Matchers {
  def validatePrediction(predictions: Seq[Double], input: Seq[LabeledPoint]) {
    val numOffPredictions = predictions.zip(input).count { case (prediction, expected) =>
      prediction != expected.label
    }
    // At least 83% of the predictions should be on.
    ((input.length - numOffPredictions).toDouble / input.length) should be > 0.83
  }

  // Test if we can correctly learn A, B where Y = logistic(A + B*X)
  test("logistic regression with SGD") {
    val nPoints = 10000
    val A = 2.0
    val B = -1.5

    val testData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 42)

    val testRDD = sc.parallelize(testData, 2)
    testRDD.cache()
    val lr = new LogisticRegressionWithSGD().setIntercept(true)
    lr.optimizer.setStepSize(10.0).setNumIterations(20)

    val model = lr.run(testRDD)

    // Test the weights
    assert(model.weights(0) ~== -1.52 relTol 0.01)
    assert(model.intercept ~== 2.00 relTol 0.01)

    val validationData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 17)
    val validationRDD = sc.parallelize(validationData, 2)
    // Test prediction on RDD.
    validatePrediction(model.predict(validationRDD.map(_.features)).collect(), validationData)

    // Test prediction on Array.
    validatePrediction(validationData.map(row => model.predict(row.features)), validationData)
  }

  // Test if we can correctly learn A, B where Y = logistic(A + B*X)
  test("logistic regression with LBFGS") {
    val nPoints = 10000
    val A = 2.0
    val B = -1.5

    val testData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 42)

    val testRDD = sc.parallelize(testData, 2)
    testRDD.cache()
    val lr = new LogisticRegressionWithLBFGS().setIntercept(true)

    val model = lr.run(testRDD)

    // Test the weights
    assert(model.weights(0) ~== -1.52 relTol 0.01)
    assert(model.intercept ~== 2.00 relTol 0.01)
    assert(model.weights(0) ~== model.weights(0) relTol 0.01)
    assert(model.intercept ~== model.intercept relTol 0.01)

    val validationData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 17)
    val validationRDD = sc.parallelize(validationData, 2)
    // Test prediction on RDD.
    validatePrediction(model.predict(validationRDD.map(_.features)).collect(), validationData)

    // Test prediction on Array.
    validatePrediction(validationData.map(row => model.predict(row.features)), validationData)
  }

  test("logistic regression with initial weights with SGD") {
    val nPoints = 10000
    val A = 2.0
    val B = -1.5

    val testData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 42)

    val initialB = -1.0
    val initialWeights = Vectors.dense(initialB)

    val testRDD = sc.parallelize(testData, 2)
    testRDD.cache()

    // Use half as many iterations as the previous test.
    val lr = new LogisticRegressionWithSGD().setIntercept(true)
    lr.optimizer.setStepSize(10.0).setNumIterations(10)

    val model = lr.run(testRDD, initialWeights)

    // Test the weights
    assert(model.weights(0) ~== -1.50 relTol 0.01)
    assert(model.intercept ~== 1.97 relTol 0.01)

    val validationData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 17)
    val validationRDD = sc.parallelize(validationData, 2)
    // Test prediction on RDD.
    validatePrediction(model.predict(validationRDD.map(_.features)).collect(), validationData)

    // Test prediction on Array.
    validatePrediction(validationData.map(row => model.predict(row.features)), validationData)
  }

  test("logistic regression with initial weights with LBFGS") {
    val nPoints = 10000
    val A = 2.0
    val B = -1.5

    val testData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 42)

    val initialB = -1.0
    val initialWeights = Vectors.dense(initialB)

    val testRDD = sc.parallelize(testData, 2)
    testRDD.cache()

    // Use half as many iterations as the previous test.
    val lr = new LogisticRegressionWithLBFGS().setIntercept(true)

    val model = lr.run(testRDD, initialWeights)

    // Test the weights
    assert(model.weights(0) ~== -1.50 relTol 0.02)
    assert(model.intercept ~== 1.97 relTol 0.02)

    val validationData = LogisticRegressionSuite.generateLogisticInput(A, B, nPoints, 17)
    val validationRDD = sc.parallelize(validationData, 2)
    // Test prediction on RDD.
    validatePrediction(model.predict(validationRDD.map(_.features)).collect(), validationData)

    // Test prediction on Array.
    validatePrediction(validationData.map(row => model.predict(row.features)), validationData)
  }
}

class LogisticRegressionClusterSuite extends FunSuite with LocalClusterSparkContext {

  test("task size should be small in both training and prediction using SGD optimizer") {
    val m = 4
    val n = 200000
    val points = sc.parallelize(0 until m, 2).mapPartitionsWithIndex { (idx, iter) =>
      val random = new Random(idx)
      iter.map(i => LabeledPoint(1.0, Vectors.dense(Array.fill(n)(random.nextDouble()))))
    }.cache()
    // If we serialize data directly in the task closure, the size of the serialized task would be
    // greater than 1MB and hence Spark would throw an error.
    val model = LogisticRegressionWithSGD.train(points, 2)

    val predictions = model.predict(points.map(_.features))

    // Materialize the RDDs
    predictions.count()
  }

  test("task size should be small in both training and prediction using LBFGS optimizer") {
    val m = 4
    val n = 200000
    val points = sc.parallelize(0 until m, 2).mapPartitionsWithIndex { (idx, iter) =>
      val random = new Random(idx)
      iter.map(i => LabeledPoint(1.0, Vectors.dense(Array.fill(n)(random.nextDouble()))))
    }.cache()
    // If we serialize data directly in the task closure, the size of the serialized task would be
    // greater than 1MB and hence Spark would throw an error.
    val model =
      (new LogisticRegressionWithLBFGS().setIntercept(true).setNumIterations(2)).run(points)

    val predictions = model.predict(points.map(_.features))

    // Materialize the RDDs
    predictions.count()
  }

}
