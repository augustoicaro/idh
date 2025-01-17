import sys
from math import *
from java.awt import *
from java.lang import *
from java.util import *
from java.nio import *
from javax.swing import *

from edu.mines.jtk.awt import *
from edu.mines.jtk.dsp import *
from edu.mines.jtk.io import *
from edu.mines.jtk.mosaic import *
from edu.mines.jtk.util import *
from edu.mines.jtk.util.ArrayMath import *

from lss import *

#############################################################################
# parameters

smNone = LocalSemblanceFilterX.Smoothing.NONE
smBoxcar = LocalSemblanceFilterX.Smoothing.BOXCAR
smGaussian = LocalSemblanceFilterX.Smoothing.GAUSSIAN
smLaplacian = LocalSemblanceFilterX.Smoothing.LAPLACIAN

d2U = LocalSemblanceFilter.Direction2.U
d2V = LocalSemblanceFilter.Direction2.V
d2UV = LocalSemblanceFilter.Direction2.UV

plotTitleBarHeight = 23
plotWidthColorBar = 80
plotWidthColorBarTotal = plotWidthColorBar+53
fClip = 9

def setTpd(): # Teapot Dome slice vertical
  global n1,n2,fileName,plotPref,dataDir,fScale
  global halfWidth,halfWidth1,halfWidth2,sigmaTensor
  global hlabel,vlabel,plotWidth,plotHeight
  n1,n2 = 251,357
  fileName = "tp73.dat"
  plotPref = "tpd"
  dataDir = "/data/seis/tp/csm/oldslices/"
  hlabel = "crossline (samples)"
  vlabel = "depth (samples)"
  fScale = fClip/4.0
  halfWidth = 4
  halfWidth1 = 1*halfWidth
  halfWidth2 = 4*halfWidth
  sigmaTensor = 8.0
  plotWidth,plotHeight = 1035,670

def setAtw(): # Atwater channels slice horizontal
  global n1,n2,fileName,plotPref,dataDir,fScale
  global halfWidth,halfWidth1,halfWidth2,sigmaTensor
  global hlabel,vlabel,plotWidth,plotHeight
  n1,n2 = 500,500
  fileName = "atwj1s.dat"
  plotPref = "atw"
  dataDir = "/data/seis/atw/"
  hlabel = "crossline (samples)"
  vlabel = "inline (samples)"
  fScale = fClip/15000.0
  halfWidth = 4
  halfWidth1 = 1*halfWidth
  halfWidth2 = 1*halfWidth
  sigmaTensor = 12.0
  #plotWidth,plotHeight = 1035,900
  plotWidth,plotHeight =  780,670

def setPlotWidthHeight():
  global plotWidth,plotHeight
  plotWidth = 900
  plotHeight = plotWidth*n1/n2
  plotWidth += plotWidthColorBarTotal
  plotHeight += plotTitleBarHeight

plotFontSize = 32
#plotPngDir = "./png/sos/"
plotPngDir = None

gray = ColorMap.GRAY
jet = ColorMap.JET
prism = ColorMap.PRISM

#############################################################################
# functions

def main(args):
  setAtw(); goAll()
  #setTpd(); goAll()
  return

def goAll():
  goImage()
  #goTensors()
  goSmoothGV()
  goSmoothHV()
  goSmoothGSV()
  goSemblanceV()
  #goSemblanceClassic()

def goImage():
  f = readImage(n1,n2,fileName)
  print "input: f min =",min(f)," max =",max(f)
  p = panel()
  pf = p.addPixels(f)
  pf.setClips(-fClip,fClip)
  frame(p,"f")

def goTensors():
  f = readImage(n1,n2,fileName)
  p = panel()
  pf = p.addPixels(f)
  pf.setClips(-fClip,fClip)
  t = computeTensors(sigmaTensor,f)
  xu1,xu2,xv1,xv2 = makeTensorVectors(t)
  pv = p.addPoints(xv1,xv2)
  pv.setLineColor(Color.YELLOW)
  pv.setLineWidth(3)
  frame(p,"fv")

def goSmoothGV():
  hw = 20
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  #f = makeRandom(n1,n2)
  #for sm in [smBoxcar,smGaussian,smLaplacian]:
  for sm in [smLaplacian]:
    lsf = makeLocalSemblanceFilter(sm,hw,sm,hw)
    g = lsf.smooth1(d2V,t,f)
    p = panel()
    pf = p.addPixels(g)
    pf.setClips(-fClip,fClip)
    frame(p,"gv"+smstr(sm)+str(hw))

def goSmoothGSV():
  hw = 20
  hw1 = halfWidth1
  hw2 = halfWidth2
  sm = smLaplacian
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  lsf = makeLocalSemblanceFilter(sm,hw1,sm,hw2)
  s = lsf.semblance(d2V,t,f)
  s = mul(s,s)
  s = mul(s,s)
  t.setEigenvalues(0.0,1.0)
  lsf = LocalSmoothingFilter()
  c = hw*(hw+1)/6.0
  g = copy(f)
  lsf.apply(t,c,s,f,g)
  p = panel()
  pf = p.addPixels(g)
  pf.setClips(-fClip,fClip)
  frame(p,"gsv"+smstr(sm)+str(hw))

def goSmoothGU():
  hw = 10
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  #f = makeRandom(n1,n2)
  for sm in [smBoxcar,smGaussian,smLaplacian]:
    lsf = makeLocalSemblanceFilter(sm,hw,sm,hw)
    g = lsf.smooth1(d2U,t,f)
    p = panel()
    pf = p.addPixels(g)
    pf.setClips(-fClip,fClip)
    frame(p,"gu"+smstr(sm)+str(hw))

def goSmoothHV():
  hw = 20 
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  f = makeImpulses(n1,n2,1+2*hw,1+2*hw)
  #f = mul(30*fClip,f)
  f = mul(100,f)
  for sm in [smLaplacian]:
    lsf = makeLocalSemblanceFilter(sm,hw,sm,hw)
    g = lsf.smooth1(d2V,t,f)
    p = panel()
    pf = p.addPixels(g)
    #pf.setClips(-fClip,fClip)
    pf.setClips(-4.0,4.0)
    frame(p,"h2v"+smstr(sm)+str(hw))
    if n1>400:
      p = panel()
      p.setLimits(320,320,499,499)
      p.setHInterval(50)
      p.setVInterval(50)
      pf = p.addPixels(g)
      #pf.setClips(-fClip,fClip)
      pf.setClips(-4.0,4.0)
      frame(p,"hvz"+smstr(sm)+str(hw))

def goSemblanceV():
  hw1 = halfWidth1
  hw2 = halfWidth2
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  #for sm1 in [smBoxcar,smGaussian,smLaplacian]:
  for sm1 in [smLaplacian]:
    sm2 = sm1
    lsf = makeLocalSemblanceFilter(sm1,hw1,sm2,hw2)
    s = lsf.semblance(d2V,t,f)
    print "s min =",min(s),"max =",max(s)
    p = panel()
    ps = p.addPixels(s)
    ps.setClips(0.0,1.0)
    frame(p,"sv"+smstr(sm1)+str(hw1)+"_"+str(hw2))

def makeLocalSemblanceFilter(sm1,hw1,sm2,hw2):
  if sm1==smLaplacian and sm2==smLaplacian:
    lsf = LocalSemblanceFilter(hw1,hw2)
  else:
    lsf = LocalSemblanceFilterX(sm1,hw1,sm2,hw2)
  return lsf

def smstr(sm):
  if sm==smBoxcar:
    return "b"
  elif sm==smGaussian:
    return "g"
  else:
    return "l"

def goSemblanceClassic():
  f = readImage(n1,n2,fileName)
  t = computeTensors(sigmaTensor,f)
  pmax = 10.0
  hw1 = halfWidth1
  hw2 = halfWidth2
  s = LocalSemblanceFilterX.semblanceForSlopes(pmax,hw1,hw2,t,f)
  p = panel()
  ps = p.addPixels(s)
  ps.setClips(0.0,1.0)
  frame(p,"ssc"+str(hw1)+"_"+str(hw2))

def computeTensors(sigma,f):
  lof = LocalOrientFilter(sigma)
  t = lof.applyForTensors(f)
  return t
 
def makeImpulses(n1,n2,k1,k2):
  m1 = n1/k1
  m2 = n2/k2
  j1 = (n1-(m1-1)*k1)/2
  j2 = (n2-(m2-1)*k2)/2
  f = zerofloat(n1,n2)
  for i2 in range(m2):
    for i1 in range(m1):
      f[j2+i2*k2][j1+i1*k1] = 1.0
  return f
 
def makeTensorVectors(t):
  k1 = 30
  k2 = 30
  s1 = k1/3.0
  s2 = k2/3.0
  m1 = n1/k1
  m2 = n2/k2
  j1 = (n1-(m1-1)*k1)/2
  j2 = (n2-(m2-1)*k2)/2
  xu1 = zerofloat(2,m1*m2)
  xu2 = zerofloat(2,m1*m2)
  xv1 = zerofloat(2,m1*m2)
  xv2 = zerofloat(2,m1*m2)
  e = zerofloat(2)
  u = zerofloat(2)
  v = zerofloat(2)
  for l2 in range(m2):
    for l1 in range(m1):
      ll = l1+l2*m1
      i1 = j1+l1*k1
      i2 = j2+l2*k2
      t.getEigenvectorU(i1,i2,u)
      t.getEigenvectorV(i1,i2,v)
      t.getEigenvalues(i1,i2,e)
      eu,ev = e[0],e[1]
      u1,u2 = u[0],u[1]
      v1,v2 = v[0],v[1]
      el = (eu-ev)/eu
      xu1[ll][0] = i1-u1*s1*el
      xu2[ll][0] = i2-u2*s2*el
      xu1[ll][1] = i1+u1*s1*el
      xu2[ll][1] = i2+u2*s2*el
      xv1[ll][0] = i1-v1*s1*el
      xv2[ll][0] = i2-v2*s2*el
      xv1[ll][1] = i1+v1*s1*el
      xv2[ll][1] = i2+v2*s2*el
  return xu1,xu2,xv1,xv2

def applyBandPassFilter(f):
  bpf = BandPassFilter(0.00,0.45,0.10,0.01)
  bpf.setExtrapolation(BandPassFilter.Extrapolation.ZERO_SLOPE)
  g = zerofloat(len(f[0]),len(f))
  bpf.apply(f,g)
  return g

def smooth(x):
  n1 = len(x[0])
  n2 = len(x)
  t = zerofloat(n1,n2)
  y = zerofloat(n1,n2)
  rgf = RecursiveGaussianFilter(1.0)
  rgf.apply0X(x,t)
  rgf.applyX0(t,y)
  return y

def makeRandom(n1,n2):
  f = sub(mul(2*fClip,randfloat(n1,n2)),fClip)
  f = mul(2.0,f)
  f = smooth(f)
  return f

def readImage(n1,n2,fileName):
  f = zerofloat(n1,n2)
  ais = ArrayInputStream(dataDir+fileName)
  ais.readFloats(f)
  ais.close()
  return mul(fScale,f)

def writeImage(f,fileName):
  aos = ArrayOutputStream(fileName)
  aos.writeFloats(f)
  aos.close()
 
#############################################################################
# plot

def panel():
  p = PlotPanel(1,1,
    PlotPanel.Orientation.X1DOWN_X2RIGHT,
    PlotPanel.AxesPlacement.NONE)
  #p = PlotPanel(1,1,
  #  PlotPanel.Orientation.X1DOWN_X2RIGHT,
  #  PlotPanel.AxesPlacement.LEFT_TOP)
  p.addColorBar()
  p.setColorBarWidthMinimum(plotWidthColorBar)
  p.setHInterval(100)
  p.setVInterval(100)
  #p.setHLabel(hlabel)
  #p.setVLabel(vlabel)
  #p.setHLabel(" ")
  #p.setVLabel(" ")
  return p

def frame(panel,png=None):
  frame = PlotFrame(panel)
  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  frame.setFontSize(plotFontSize)
  frame.setSize(plotWidth,plotHeight)
  frame.setVisible(True)
  if png and plotPngDir:
    frame.paintToPng(200,6,plotPngDir+plotPref+png+".png")
  return frame

#############################################################################
# Do everything on Swing thread.

class RunMain(Runnable):
  def run(self):
    main(sys.argv)
SwingUtilities.invokeLater(RunMain())
