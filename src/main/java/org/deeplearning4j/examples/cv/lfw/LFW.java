package org.deeplearning4j.examples.cv.lfw;

import org.canova.api.io.labels.ParentPathLabelGenerator;
import org.canova.image.loader.LFWLoader;
import org.deeplearning4j.AlexNet;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.datasets.iterator.MultipleEpochsIterator;
import org.deeplearning4j.datasets.iterator.impl.LFWDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.conf.layers.setup.ConvolutionLayerSetup;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Labeled Faces in the Wild
 *
 * Dataset created by Erik Learned-Miller, Gary Huang, Aruni RoyChowdhury,
 * Haoxiang Li, Gang Hua. This is used to study unconstrained face recognition.
 * Each face has been labeled with the name of the person pictured.
 *
 * Over 13K images
 * 5749 unique classes (different people)
 * 1680 people have 2+ photos
 *
 * References:
 * General information is at http://vis-www.cs.umass.edu/lfw/. Architecture partially based on DeepFace:
 * http://mmlab.ie.cuhk.edu.hk/pdf/YiSun_CVPR14.pdf
 *
 * Note: this is a sparse dataset with only 1 example for many of the faces; thus, performance is low.
 * Ideally train on a larger dataset like celebs to get params and/or generate variations of the image examples.
 *
 * Currently set to only use the subset images, names starting with A.
 * Switch to NUM_LABELS & NUM_IMAGES and set subset to false to use full dataset.
 */

public class LFW {
    private static final Logger log = LoggerFactory.getLogger(LFW.class);

    public static void main(String[] args) {

        final int numRows = 32;
        final int numColumns = 32;
        final int nChannels = 3;
        int outputNum = LFWLoader.SUB_NUM_LABELS;
        int numSamples = 4;// LFWLoader.SUB_NUM_IMAGES; // LFWLoader.NUM_IMAGES-33;
        boolean useSubset = true;
        double splitTrainTest = 0.5;
        int batchSize = 2;
        int iterations = 1;
        int seed = 42;
        int epochs = 2;
        int listenerFreq = iterations/5;

        log.info("Load data training data....");
        LFWDataSetIterator lfw = new LFWDataSetIterator(batchSize, numSamples, new int[] {numRows, numColumns, nChannels}, outputNum, useSubset, new ParentPathLabelGenerator(), true, splitTrainTest, null, 255, new Random(seed));

        log.info("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(iterations)
                .activation("relu")
                .weightInit(WeightInit.XAVIER)
                .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.01)
                .momentum(0.9)
                .regularization(true)
                .l2(1e-3)
                .updater(Updater.ADAGRAD)
                .useDropConnect(true)
                .list()
                .layer(0, new ConvolutionLayer.Builder(4, 4)
                        .name("cnn1")
                        .nIn(nChannels)
                        .stride(1, 1)
                        .nOut(20)
                        .build())
                .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .name("pool1")
                        .build())
                .layer(2, new ConvolutionLayer.Builder(3, 3)
                        .name("cnn2")
                        .stride(1,1)
                        .nOut(40)
                        .build())
                .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .name("pool2")
                        .build())
                .layer(4, new ConvolutionLayer.Builder(3, 3)
                        .name("cnn3")
                        .stride(1,1)
                        .nOut(60)
                        .build())
                .layer(5, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{2, 2})
                        .name("pool3")
                        .build())
                .layer(6, new ConvolutionLayer.Builder(2, 2)
                        .name("cnn3")
                        .stride(1,1)
                        .nOut(80)
                        .build())
                .layer(7, new DenseLayer.Builder()
                        .name("ffn1")
                        .nOut(160)
                        .dropOut(0.5)
                        .build())
                .layer(8, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nOut(outputNum)
                        .activation("softmax")
                        .build())
                .backprop(true).pretrain(false)
                .cnnInputSize(numRows,numColumns,nChannels)
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
//        MultiLayerNetwork model = new AlexNet(numRows, numColumns, nChannels, outputNum, seed, iterations).init();
        model.init();

        log.info("Train model....");
        model.setListeners(Collections.singletonList((IterationListener) new ScoreIterationListener(listenerFreq)));
        MultipleEpochsIterator multiLFW = new MultipleEpochsIterator(epochs, lfw, 5);
        model.fit(multiLFW);

        log.info("Load data testing data....");
        lfw = new LFWDataSetIterator(batchSize, numSamples, new int[] {numRows, numColumns, nChannels}, outputNum, useSubset, new ParentPathLabelGenerator(), false, splitTrainTest, null, 255, new Random(seed));


        log.info("Evaluate model....");
        Evaluation eval = model.evaluate(lfw, lfw.getLabels());
        log.info(eval.stats());
        log.info("****************Example finished********************");

    }

}
