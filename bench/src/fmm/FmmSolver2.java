/****************************************************************************
Copyright (c) 2008, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is 
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package fmm;

import edu.mines.jtk.util.*;
import static edu.mines.jtk.util.MathPlus.*;

// for testing
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import edu.mines.jtk.awt.*;
import edu.mines.jtk.dsp.*;
import edu.mines.jtk.io.*;
import edu.mines.jtk.mosaic.*;

/**
 * 2D implementation of the fast marching method.
 * @author Dave Hale, Colorado School of Mines
 * @version 2008.07.14
 */
public class FmmSolver2 {

  /**
   * Either 4 or 8 neighbors are used in finite-difference stencil for solver.
   */
  public enum Stencil {
    FOUR,
    EIGHT
  };

  /**
   * An interface for classes of structure tensors. Each tensor is a
   * symmetric positive-definite 2-by-2 matrix {{s11,s12},{s12,s22}}.
   */
  public interface Tensors {

    /**
     * Gets structure tensor elements for specified indices.
     * @param i1 index for 1st dimension.
     * @param i2 index for 2nd dimension.
     * @param d array {s11,s12,s22} of tensor elements.
     */
    public void getTensor(int i1, int i2, float[] d);
  }

  /**
   * Constructs a solver with constant identity structure tensors.
   * All times are initially infinite (very large).
   * @param n1 number of samples in 1st dimension.
   * @param n2 number of samples in 2nd dimension.
   */
  public FmmSolver2(int n1, int n2, Stencil stencil) {
    init(n1,n2,null,stencil,new IdentityTensors());
  }
  
  /**
   * Constructs a solver for the specified structure tensor field.
   * All times are initially infinite (very large).
   * @param n1 number of samples in 1st dimension.
   * @param n2 number of samples in 2nd dimension.
   * @param tensors structure tensors.
   */
  public FmmSolver2(int n1, int n2, Stencil stencil, Tensors tensors) {
    init(n1,n2,null,stencil,tensors);
  }
  
  /**
   * Constructs a solver for a specified array of times.
   * The array is referenced (not copied) by this solver.
   * @param t array of times to be updated by this solver; 
   * @param tensors structure tensors.
   */
  public FmmSolver2(float[][] t, Stencil stencil, Tensors tensors) {
    init(t[0].length,t.length,t,stencil,tensors);
  }

  /**
   * Zeros the time at the specified sample and updates times elsewhere.
   * @param i1 index in 1st dimension of time to zero.
   * @param i2 index in 2nd dimension of time to zero.
   * @return the updated array of times; by reference, not by copy.
   */
  public float[][] zeroAt(int i1, int i2) {
    clearMarks();
    _heap.clear();
    _mark[i2][i1] = _known;
    _t[i2][i1] = 0.0f;
    updateNabors(i1,i2);
    while (!_heap.isEmpty()) {
      TimeHeap2.Entry e = _heap.remove();
      i1 = e.i1;
      i2 = e.i2;
      _mark[i2][i1] = _known;
      updateNabors(i1,i2);
    }
    return _t;
  }

  /**
   * Gets the array of times computed by this solver.
   * @return array of times; by reference, not by copy.
   */
  public float[][] getTimes() {
    return _t;
  }

  ///////////////////////////////////////////////////////////////////////////
  // private

  // Structure tensors.
  private static class IdentityTensors implements Tensors {
    public void getTensor(int i1, int i2, float[] s) {
      s[0] = 1.00f; // s11
      s[1] = 0.00f; // s12
      s[2] = 1.00f; // s22
    }
  }

  // Initial huge value for times not yet computed.
  private static final float INFINITY = Float.MAX_VALUE;

  // Times for each sample are computed using either four or eight
  // neighbor samples and triangles triangles. The triangles are 
  // indexed as follows:
  //     4 neighbors           8 neighbors
  //       2 ^                    2 ^
  //         *                * - - * - - *
  //       / | \              | \ 2 | 1 / | 
  //     / 1 | 0 \            | 3 \ | / 0 |
  //   * - - X - - * >        * - - X - - * >
  //     \ 2 | 3 /   1        | 4 / | \ 7 | 1
  //       \ | /              | / 5 | 6 \ | 
  //         *                * - - * - - *
  // In either case, the symbol X represents the vertex X0 shared by 
  // all neighbor triangles; and the symbols * represent the other two 
  // triangle vertices X1 and X2.

  // Sample index offsets for 4-neighbor samples.
  private static final int[] K41 = { 1, 0,-1, 0};
  private static final int[] K42 = { 0, 1, 0,-1};

  // Sample index offsets for vertices X1 of 4-neighbor tris.
  private static final int[] K411 = { 1, 0,-1, 0};
  private static final int[] K412 = { 0, 1, 0,-1};

  // Sample index offsets for vertices X2 of 4-neighbor tris.
  private static final int[] K421 = { 0,-1, 0, 1};
  private static final int[] K422 = { 1, 0,-1, 0};

  // Indices of 4-neighbor triangles.
  private static final int[][] K4T  = {{1,2},{2,3},{3,0},{0,1}};

  // Sample index offsets for 8-neighbor samples.
  private static final int[] K81 = { 1, 1, 0,-1,-1,-1, 0, 1};
  private static final int[] K82 = { 0, 1, 1, 1, 0,-1,-1,-1};

  // Sample index offsets for vertices X1 of 8-neighbor tris.
  private static final int[] K811 = { 1, 1, 0,-1,-1,-1, 0, 1};
  private static final int[] K812 = { 0, 1, 1, 1, 0,-1,-1,-1};

  // Sample index offsets for vertices X2 of 8-neighbor tris.
  private static final int[] K821 = { 1, 0,-1,-1,-1, 0, 1, 1};
  private static final int[] K822 = { 1, 1, 1, 0,-1,-1,-1, 0};

  // Indices of 8-neighbor triangles.
  private static final int[][] K8T  = {
    {3,4},{4,5},{5,6},{6,7},{7,0},{0,1},{1,2},{2,3}
  };

  private int _n1,_n2; // numbers of samples in each dimension
  private float[][] _t; // array of times computed by this solver
  private Tensors _tensors; // structure tensors
  private int[][] _mark; // samples are marked far, trial, or known
  private TimeHeap2 _heap; // min-heap of sample indices and times 
  private int[] _k1,_k2,_k11,_k12,_k21,_k22; // indices of neighbor samples
  private int[][] _kt; // indices of neighbor tris
  private int _nk; // number of sample neighbors, either 4 or 8

  // Marks used during computation of times. For efficiency, we do not
  // loop over all the marks to clear them before beginning a fast 
  // marching loop. Instead, we simply modify the mark values.
  private int _far = 0; // samples with no time
  private int _trial = 1; // samples in min-heap with a proposed time
  private int _known = 2; // samples with a known time
  private void clearMarks() {
    if (_known+2>Integer.MAX_VALUE) { // if we must loop over all marks, ...
      _far = 0;
      _trial = 1;
      _known = 2;
      for (int i2=0; i2<_n2; ++i2) {
        for (int i1=0; i1<_n1; ++i1) {
          _mark[i2][i1] = _far;
        }
      }
    } else {
      _far += 2; // all known samples instantly become far samples
      _trial +=2; // no samples are trial
      _known +=2; // no samples are known
    }
  }

  private void init(
    int n1, int n2, float[][] t, Stencil stencil, Tensors tensors) 
  {
    _n1 = n1;
    _n2 = n2;
    _t = t;
    _tensors = tensors;
    if (stencil==Stencil.FOUR) {
      _k1 = K41; _k2 = K42;
      _k11 = K411; _k12 = K412;
      _k21 = K421; _k22 = K422;
      _kt = K4T;
      _nk = 4;
    } else {
      _k1 = K81; _k2 = K82;
      _k11 = K811; _k12 = K812;
      _k21 = K821; _k22 = K822;
      _kt = K8T;
      _nk = 8;
    }
    if (_t==null) 
      _t = Array.fillfloat(INFINITY,n1,n2);
    _mark = new int[_n2][_n1];
    _heap = new TimeHeap2(TimeHeap2.Type.MIN,_n1,_n2);
  }

  private void updateNabors(int i1, int i2) {
    for (int k=0; k<_nk; ++k) {

      // Neighbor sample indices (j1,j2); skip if out of bounds.
      int j1 = i1+_k1[k];
      int j2 = i2+_k2[k];
      if (j1<0 || j1>=_n1) continue;
      if (j2<0 || j2>=_n2) continue;

      // If time for neighbor not already known, update it.
      if (_mark[j2][j1]!=_known)
        updateTime(j1,j2,_kt[k]);
    }
  }

  /**
   * Updates the time for one sample using known times in neighbor tris.
   * The array kt contains indices in [0,3] of relevant neighbor tris.
   */
  private void updateTime(int j1, int j2, int[] kt) {

    // Elements of structure tensor.
    float[] s = new float[3];
    _tensors.getTensor(j1,j2,s);
    float s11 = s[0];
    float s12 = s[1];
    float s22 = s[2];

    // The current minimum time.
    float tmin = _t[j2][j1];

    // Initally assume that no computed time will be less than current min.
    boolean smallerTimeFound = false;

    // For all relevant neighbor tris, ...
    for (int it=0; it<kt.length; ++it) {
      int jt = kt[it];

      // Sample indices of vertices X0, X1, and X2 of neighbor tri.
      int j01 = j1;
      int j02 = j2;
      int j11 = j1+_k11[jt];
      int j12 = j2+_k12[jt];
      int j21 = j1+_k21[jt];
      int j22 = j2+_k22[jt];

      // All indices must be in bounds.
      if (j11<0 || j11>=_n1) continue;
      if (j12<0 || j12>=_n2) continue;
      if (j21<0 || j21>=_n1) continue;
      if (j22<0 || j22>=_n2) continue;

      // Which neighbor vertices are known? (At least one must be!)
      int m1 = _mark[j12][j11];
      int m2 = _mark[j22][j21];

      // Times T0, T1, and T2 at vertices X0, X1, and X2 of tri.
      float t0 = INFINITY;
      float t1 = _t[j12][j11];
      float t2 = _t[j22][j21];

      // Use only known times in {T1,T2} to compute candidate time T0.
      if (m1==_known) {
        if (m2==_known) { // use edge 12
          t0 = computeTime(s11,s12,s22,t1-t2,t2,
                           j11-j21,j12-j22,
                           j01-j21,j02-j22);
        } else { // use vertex 1
          t0 = computeTime(s11,s12,s22,t1,j01-j11,j02-j12);
        }
      } else if (m2==_known) { // use vertex 2
        t0 = computeTime(s11,s12,s22,t2,j01-j21,j02-j22);
      }

      // If computed time T0 is smaller than the min time, update the min time.
      if (t0<tmin) {
        tmin = t0;
        smallerTimeFound = true;
      }
    }

    // If a smaller time has been found, ...
    if (smallerTimeFound) {

      // If not been here before, so this sample not already in the min-heap, 
      // then insert this sample into the min-heap, and if the time list is
      // not null, save the current stored time so it can be restored later.
      if (_mark[j2][j1]!=_trial) {
        _mark[j2][j1] = _trial;
        _heap.insert(j1,j2,tmin);
      }

      // Else, simply reduce the time already stored in the min-heap.
      else {
        _heap.reduce(j1,j2,tmin);
      }

      // Store the smaller time.
      _t[j2][j1] = tmin;
    }
  }

  private static int N_COMPUTE_TIME1 = 0; // DEBUG
  private static int N_COMPUTE_TIME2 = 0; // DEBUG

  // Returns the time t+sqrt(y'*S*y).
  private static float computeTime(
    float s11, float s12, float s22,
    float t, float y1, float y2)
  {
    ++N_COMPUTE_TIME1;
    float z1 = s11*y1+s12*y2;
    float z2 = s12*y1+s22*y2;
    float dd = y1*z1+y2*z2;
    return t+sqrt(dd);
  }

  // Returns the minimum time 
  // t(a) = u2+a*u1+sqrt((y2-a*y1)'*S*(y2-a*y1))
  // subject to the constraint 0 <= a <= 1.
  private static float computeTime(
    float s11, float s12, float s22, float u1, float u2,
    float y11, float y12, float y21, float y22)
  {
    ++N_COMPUTE_TIME2;
    float z11 = s11*y11+s12*y12;
    float z12 = s12*y11+s22*y12;
    float z21 = s11*y21+s12*y22;
    float z22 = s12*y21+s22*y22;
    float d11 = y11*z11+y12*z12;
    float d12 = y11*z21+y12*z22;
    float d22 = y21*z21+y22*z22;
    float alpha = computeAlpha(u1,d11,d12,d22);
    return u2+alpha*u1+sqrt(d22-2.0f*alpha*d12+alpha*alpha*d11);
  }

  // Returns the parameter a (alpha) that minimizes
  // t(a) = a*u1+sqrt(d22-2.0f*a*d12+a*a*d11)
  // subject to the constraint 0 <= a <= 1.
  private static float computeAlpha(
    float u1, float d11, float d12, float d22) 
  {
    float alpha;
    float dd = d11*d22-d12*d12;
    if (dd<0.0f)
      dd = 0.0f;
    float du = d11-u1*u1;
    if (du<=0.0f) {
      alpha = (u1>=0.0f)?0.0f:1.0f;
    } else {
      alpha = (d12-u1*sqrt(dd/du))/d11;
      if (alpha<=0.0f) {
        alpha = 0.0f;
      } else if (alpha>=1.0f) {
        alpha = 1.0f;
      }
    }
    return alpha;
  }

  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  // testing

  private static class ConstantTensors implements FmmSolver2.Tensors {
    ConstantTensors(float d11, float d12, float d22) {
      _d11 = d11;
      _d12 = d12;
      _d22 = d22;
    }
    public void getTensor(int i1, int i2, float[] d) {
      d[0] = _d11;
      d[1] = _d12;
      d[2] = _d22;
    }
    private float _d11,_d12,_d22;
  }

  private static void plot(float[][] x) {
    float[][] y = Array.copy(x);
    int n1 = y[0].length;
    int n2 = y.length;
    for (int i2=0; i2<n2; ++i2) {
      for (int i1=0; i1<n1; ++i1) {
        if (y[i2][i1]==INFINITY) {
          y[i2][i1] = 0.0f;
        }
      }
    }
    SimplePlot sp = new SimplePlot();
    sp.setSize(800,790);
    PixelsView pv = sp.addPixels(y);
    pv.setColorModel(ColorMap.JET);
    //pv.setInterpolation(PixelsView.Interpolation.NEAREST);
    pv.setInterpolation(PixelsView.Interpolation.LINEAR);
  }

  private static void testConstant() {
    int n1 = 501;
    int n2 = 501;
    float angle = FLT_PI*110.0f/180.0f;
    float su = 0.010f;
    float sv = 1.000f;
    float cosa = cos(angle);
    float sina = sin(angle);
    float d11 = su*cosa*cosa+sv*sina*sina;
    float d12 = (su-sv)*sina*cosa;
    float d22 = sv*cosa*cosa+su*sina*sina;
    trace("d11="+d11+" d12="+d12+" d22="+d22+" d="+(d11*d22-d12*d12));
    FmmSolver2.Stencil stencil = FmmSolver2.Stencil.FOUR;
    ConstantTensors tensors = new ConstantTensors(d11,d12,d22);
    FmmSolver2 fs = new FmmSolver2(n1,n2,stencil,tensors);
    Stopwatch sw = new Stopwatch();
    sw.start();
    fs.zeroAt(2*n1/4,2*n2/4);
    //fs.zeroAt(1*n1/4,1*n2/4);
    //fs.zeroAt(3*n1/4,3*n2/4);
    sw.stop();
    int nct1 = N_COMPUTE_TIME1;
    int nct2 = N_COMPUTE_TIME2;
    int nct = nct1+nct2;
    trace("time="+sw.time()+" nct="+nct+" nct1="+nct1+" nct2="+nct2);
    float[][] t = fs.getTimes();
    //Array.dump(t);
    plot(t);
  }

  private static void trace(String s) {
    System.out.println(s);
  }
  private static void trace(int i1, int i2, String s) {
    //if (i1==2 && i2==2)
    //  trace(s);
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        testConstant();
      }
    });
  }
}
