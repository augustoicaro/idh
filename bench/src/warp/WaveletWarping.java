/****************************************************************************
Copyright (c) 2014, Colorado School of Mines and others. All rights reserved.
This program and accompanying materials are made available under the terms of
the Common Public License - v1.0, which accompanies this distribution, and is
available at http://www.eclipse.org/legal/cpl-v10.html
****************************************************************************/
package warp;

import edu.mines.jtk.dsp.*;
import edu.mines.jtk.lapack.*;
import edu.mines.jtk.util.Check;
import static edu.mines.jtk.dsp.Conv.*;
import static edu.mines.jtk.util.ArrayMath.*;

/**
 * Estimates a wavelet from alignment by warping of sequences or images.
 * The two sequences or images are assumed to have been convolved with the
 * same wavelet. Warping of one sequence or image to align with the other will
 * cause the wavelet to be stretched or squeezed, and this distortion enables
 * us to estimate the wavelet.
 * <p>
 * For images, convolution with the wavelet is assumed to be in only the 1st
 * dimension. For definiteness, this 1st dimension is assumed to be time in
 * the documentation below.
 *
 * @author Dave Hale, Colorado School of Mines
 * @version 2014.01.27
 */
public class WaveletWarping {

  /**
   * Sets the min-max range of times used to estimate wavelet.
   * @param itmin minimum time, in samples.
   * @param itmax maximum time, in samples.
   */
  public void setTimeRange(int itmin, int itmax) {
    _itmin = itmin;
    _itmax = itmax;
  }

  /**
   * Sets the min-max range of frequencies used to estimate the wavelet.
   * If the specified min-max bounds on frequency are not a subset of the
   * zero-Nyquist range [0,0.5], then no bandpass filter is used. The default
   * is to use no bandpass filter.
   * @param fmin minimum frequency, in cycles/sample.
   * @param fmax maximum frequency, in cycles/sample.
   */
  public void setFrequencyRange(double fmin, double fmax) {
    if (fmin<fmax && (0.0<fmin || fmax<0.5)) {
      _bpf = new BandPassFilter(fmin,fmax,0.05,0.01);
    } else {
      _bpf = null;
    }
  }

  /**
   * Sets the stability factor by which to scale zero-lag of correlations.
   * A factor slightly greater than one may stabilize estimates of
   * inverse wavelets A.
   * @param sfac stability factor.
   */
  public void setStabilityFactor(double sfac) {
    _sfac = sfac;
  }

  /**
   * Returns inverse wavelet a estimated by warping one sequence to another.
   * The sequences are related by warping such that f[t] ~ g[u[t]].
   * @param na number of samples in the inverse wavelet a.
   * @param ka the sample index for a[0].
   * @param u array of samples for warping u[t].
   * @param f array of samples for sequence f[t].
   * @param g array of samples for sequence g[t]
   * @return array of coefficients for the inverse wavelet a.
   */
  public float[] getInverseA(
    int na, int ka, float[] u, float[] f, float[] g)
  {
    int nt = u.length;
    Check.argument(-na<ka,"-na<ka");
    Check.argument(ka<=0,"ka<=0");

    // Differences d for all lags of inverse wavelet a.
    float[][] d = computeDifferences(na,ka,u,f,g);

    // The matrix C and right-hand-side vector b, for Ca = b. For zero lag, we
    // have a0 = a[-ka] = 1, so that only na-1 coefficients of a are unknown;
    // the unknown coefficients are those corresponding to non-zero lags.
    int ma = na-1;
    DMatrix c = new DMatrix(ma,ma);
    DMatrix b = new DMatrix(ma,1);
    for (int ia=0,ic=0; ia<na; ++ia) {
      if (ia==-ka) continue; // skip lag zero, because a0 = 1
      for (int ja=0,jc=0; ja<na; ++ja) {
        if (ja==-ka) continue; // skip lag zero, because a0 = 1
        double cij = dot(d[ia],d[ja]);
        c.set(ic,jc,cij);
        ++jc;
      }
      c.set(ic,ic,c.get(ic,ic)*_sfac);
      double bi = -dot(d[ia],d[-ka]);
      b.set(ic,0,bi);
      ++ic;
    }
    //System.out.println("c=\n"+c);
    //System.out.println("b=\n"+b);

    // Solve for inverse filter a using Cholesky decomposition of C.
    DMatrixChd chd = new DMatrixChd(c);
    DMatrix a = chd.solve(b);
    float[] aa = new float[na];
    for (int ia=0,ic=0; ia<na; ++ia) {
      if (ia==-ka) {
        aa[ia] = 1.0f; // lag 0, so a0 = 1
      } else {
        aa[ia] = (float)a.get(ic,0);
        ++ic;
      }
    }
    return aa;
  }

  /**
   * Returns inverse wavelet a estimated via PEF of sequence.
   * @param na number of samples in the inverse wavelet a.
   * @param ka the sample index for a[0].
   * @param f array of samples for sequence f(t).
   * @return array of coefficients for the inverse wavelet a.
   */
  public float[] getInverseAPef(int na, int ka, float[] f) {
    int nt = f.length;

    // Sequence for different time shifts
    float[][] d = new float[na][nt];
    for (int ia=0; ia<na; ++ia) {
      d[ia] = delay(ka+ia,f);
    }

    // The matrix C and right-hand-side vector b, for Ca = b. For zero lag, we
    // have a0 = a[-ka] = 1, so that only na-1 coefficients of a are unknown;
    // the unknown coefficients are those corresponding to non-zero lags.
    int ma = na-1;
    DMatrix c = new DMatrix(ma,ma);
    DMatrix b = new DMatrix(ma,1);
    for (int ia=0,ic=0; ia<na; ++ia) {
      if (ia==-ka) continue; // skip lag zero, because a0 = 1
      for (int ja=0,jc=0; ja<na; ++ja) {
        if (ja==-ka) continue; // skip lag zero, because a0 = 1
        double cij = dot(d[ia],d[ja]);
        c.set(ic,jc,cij);
        ++jc;
      }
      c.set(ic,ic,c.get(ic,ic)*_sfac);
      double bi = -dot(d[ia],d[-ka]);
      b.set(ic,0,bi);
      ++ic;
    }
    //System.out.println("c=\n"+c);
    //System.out.println("b=\n"+b);

    // Solve for inverse filter a using Cholesky decomposition of C.
    DMatrixChd chd = new DMatrixChd(c);
    DMatrix a = chd.solve(b);
    float[] aa = new float[na];
    for (int ia=0,ic=0; ia<na; ++ia) {
      if (ia==-ka) {
        aa[ia] = 1.0f; // lag 0, so a0 = 1
      } else {
        aa[ia] = (float)a.get(ic,0);
        ++ic;
      }
    }
    return aa;
  }

  /**
   * Estimates the wavelet h from the inverse wavelet a.
   * @param na number of samples in the inverse wavelet a.
   * @param ka the sample index for a[0].
   * @param a array of coefficients for the inverse wavelet a.
   * @param nh number of samples in the wavelet h.
   * @param kh the sample index for h[0].
   */
  public float[] getWaveletH(int na, int ka, float[] a, int nh, int kh) {
    float[] one = {1.0f};
    float[] ca1 = new float[nh];
    float[] caa = new float[nh];
    xcor(na,ka,a,1,0,one,nh,kh,ca1);
    xcor(na,ka,a,na,ka,a,nh, 0,caa);
    caa[0] *= _sfac;
    SymmetricToeplitzFMatrix stm = new SymmetricToeplitzFMatrix(caa);
    return stm.solve(ca1);
  }

  /**
   * Applies the specified inverse wavelet A.
   * @param f array with input sequence f(t).
   * @return array with filtered output sequence.
   */
  public float[] applyA(int na, int ka, float[] a, float[] f) {
    return convolve(na,ka,a,f);
  }

  /**
   * Applies the specified wavelet H.
   * @param f array with input sequence f(t).
   * @return array with filtered output sequence.
   */
  public float[] applyH(int nh, int kh, float[] h, float[] f) {
    return convolve(nh,kh,h,f);
  }

  /**
   * Applies the bandpass filter B, if any was specified.
   * If no bandpass filter has been specified, then this method simply returns
   * a copy of the specified input sequence.
   * @param f array with input sequence f(t).
   * @return array with filtered output sequence.
   */
  public float[] applyB(float[] f) {
    float[] g = new float[f.length];
    if (_bpf!=null) {
      _bpf.apply(f,g);
    } else {
      copy(f,g);
    }
    return g;
  }

  /**
   * Applies the low-pass anti-alias filter L.
   * If the specified warping includes squeezing, then this method attenuates
   * high frequencies that could be aliased during warping.
   * @param u array of warping times u(t).
   * @param f array with input sequence f(t).
   * @return array with filtered output sequence.
   */
  public float[] applyL(float[] u, float[] f) {
    return aaf(RMAX,u,f);
  }

  /**
   * Applies the warping operator S.
   * Does not apply an anti-alias low-pass filter.
   * @param u array of warping times u(t).
   * @param f array with input sequence f(t).
   * @return array with warped output sequence.
   */
  public float[] applyS(float[] u, float[] f) {
    return warp(u,f);
  }

  /**
   * Applies the composite linear operator HSLA.
   * The sequence of operations is (1) convolution with the inverse wavelet a,
   * (2) anti-alias filtering (if squeezing), (3) warping, and (4) convolution
   * with the wavelet h.
   * @param na number of samples in the inverse wavelet a.
   * @param ka the sample index for a[0].
   * @param a array of coefficients for the inverse wavelet a.
   * @param nh number of samples in the wavelet h.
   * @param kh the sample index for h[0].
   * @param h array of coefficients for the wavelet h.
   * @param u array[nt] of warping times u(t).
   * @param f array[nt] with input sequence.
   * @return array[nt] with output sequence.
   */
  public float[] applyHSLA(
    int na, int ka, float[] a,
    int nh, int kh, float[] h,
    float[] u, float[] f) 
  {
    int nt = f.length;
    float[] af = applyA(na,ka,a,f);
    float[] laf = applyL(u,af);
    float[] saf = applyS(u,laf);
    float[] hsaf = applyH(nh,kh,h,saf);
    return hsaf;
  }

  /**
   * Applies the composite linear operator BSLA.
   * The sequence of operations is (1) convolution with the inverse wavelet a,
   * (2) anti-alias filtering (if squeezing), (3) warping, and (4) application
   * of the bandpass filter b.
   * @param na number of samples in the inverse wavelet a.
   * @param ka the sample index for a[0].
   * @param a array of coefficients for the inverse wavelet a.
   * @param u array[nt] of warping times u(t).
   * @param f array[nt] with input sequence.
   * @return array[nt] with output sequence.
   */
  public float[] applyBSLA(int na, int ka, float[] a, float[] u, float[] f) {
    int nt = f.length;
    float[] af = applyA(na,ka,a,f);
    float[] laf = applyL(u,af);
    float[] saf = applyS(u,laf);
    float[] bsaf = applyB(saf);
    return bsaf;
  }

  ///////////////////////////////////////////////////////////////////////////
  // private

  private static final float RMAX = 10.0f; // limits anti-alias filter
  private static final SincInterp _si = 
    SincInterp.fromErrorAndFrequency(0.01,0.40);

  private double _sfac = 1.0;
  private int _itmin = -1;
  private int _itmax = -1;
  private BandPassFilter _bpf;

  /**
   * Returns the array of differences D = B(SLG-F).
   */
  private float[][] computeDifferences(
    int na, int ka, float[] u, float[] f, float[] g)
  {
    g = applyL(u,g);
    int nt = u.length;
    float[][] d = new float[na][];
    for (int ia=0,lag=ka; ia<na; ++ia,++lag) {
      float[] df = delay(lag,f);
      float[] dg = delay(lag,g);
      float[] sdg = applyS(u,dg);
      float[] di = sub(sdg,df);
      d[ia] = applyB(di);
    }
    return d;
  }

  /**
   * Returns the largest squeezing r(t) = u'(t) not greater than rmax.
   * If less than or equal to one, then no squeezing is implied by u(t).
   */
  private static float squeezing(float rmax, float[] u) {
    int nt = u.length;
    float r = u[1]-u[0];
    for (int it=1; it<nt; ++it) {
      float du = u[it]-u[it-1];
      if (du>r)
        r = du;
    }
    return min(r,rmax);
  }

  /**
   * If necessary, applies an anti-alias filter to the sequence x(t).
   * An anti-alias filter is necessary if the warping includes squeezing.
   */
  private static float[] aaf(float rmax, float[] u, float[] x) {
    int nt = u.length;
    float r = squeezing(RMAX,u);
    if (r>1.0) {
      float[] y = new float[nt];
      BandPassFilter aaf = new BandPassFilter(0.0,0.5/r,0.10/r,0.01);
      aaf.apply(x,y);
      return y;
    } else {
      return copy(x);
    }
  }

  /**
   * Returns y(t) = x(t-lag).
   */
  private static float[] delay(int lag, float[] x) {
    int nt = x.length;
    int itlo = max(0,lag);   // 0 <= it-lag
    int ithi = min(nt,nt+lag); // it-lag < nt
    float[] y = new float[nt];
    for (int it=0; it<itlo; ++it)
      y[it] = 0.0f;
    for (int it=itlo; it<ithi; ++it)
      y[it] = x[it-lag];
    for (int it=ithi; it<nt; ++it)
      y[it] = 0.0f;
    return y;
  }

  /**
   * Returns y(t) = x(u(t)).
   */
  private static float[] warp(float[] u, float[] x) {
    int nt = u.length;
    float[] y = new float[nt];
    _si.interpolate(nt,1.0,0.0,x,nt,u,y);
    y[0] *= u[1]-u[0];
    for (int it=1; it<nt; ++it)
      y[it] *= u[it]-u[it-1];
    return y;
  }

  /**
   * Returns y(t) = h(t)*x(t), where * denotes convolution.
   */
  private static float[] convolve(int nh, int kh, float[] h, float[] x) {
    int nt = x.length;
    float[] y = new float[nt];
    convolve(nh,kh,h,x,y);
    return y;
  }
  private static void convolve(
    int nh, int kh, float[] h, float[] f,  float[] g)
  {
    int nt = f.length;
    conv(nh,kh,h,nt,0,f,nt,0,g);
  }

  private double dot(float[] x, float[] y) {
    int nt = x.length;
    int itlo = (_itmin<_itmax)?_itmin:0;
    int ithi = (_itmin<_itmax)?_itmax:nt-1;
    double sum = 0.0;
    for (int it=itlo; it<=ithi; ++it) 
      sum += x[it]*y[it];
    return sum;
  }

  private float rms(float[] x) {
    int nt = x.length;
    return (float)sqrt(dot(x,x)/nt);
  }
}