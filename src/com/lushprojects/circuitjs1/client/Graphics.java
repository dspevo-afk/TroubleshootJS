/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.dom.client.Context2d;

public class Graphics {
	  /** Immutable browser-measured text state for render-only label placement. */
	  static final class MeasuredText {
	      final String id, text, font, fillStyle, textAlign, textBaseline, metricKind;
	      final int ownerX, ownerY, originalX, originalY, fontSize;
	      final double width, left, right, ascent, descent, alpha;
	      final double a, b, c, d, e, f;
	      MeasuredText(String id, String text, int ownerX, int ownerY, int x, int y,
	              int fontSize, double[] m, String[] state) {
	          this.id=id; this.text=text; this.ownerX=ownerX; this.ownerY=ownerY;
	          originalX=x; originalY=y; this.fontSize=fontSize;
	          width=m[0]; left=m[1]; right=m[2]; ascent=m[3]; descent=m[4];
	          a=m[5]; b=m[6]; c=m[7]; d=m[8]; e=m[9]; f=m[10]; alpha=m[11];
	          metricKind=m[12] == 1 ? "CANVAS_ACTUAL" : "WIDTH_FONT_HEIGHT_FALLBACK";
	          font=state[0]; fillStyle=state[1]; textAlign=state[2]; textBaseline=state[3];
	      }
	      Rectangle transformRect(Rectangle r) {
	          double[][] p={{r.x,r.y},{r.x+r.width,r.y},{r.x,r.y+r.height},
	              {r.x+r.width,r.y+r.height}};
	          int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
	          for(int i=0;i<p.length;i++) {
	              int x=(int)Math.round(a*p[i][0]+c*p[i][1]+e);
	              int y=(int)Math.round(b*p[i][0]+d*p[i][1]+f);
	              minX=Math.min(minX,x);maxX=Math.max(maxX,x);
	              minY=Math.min(minY,y);maxY=Math.max(maxY,y);
	          }
	          return new Rectangle(minX,minY,Math.max(1,maxX-minX),Math.max(1,maxY-minY));
	      }
	  }
	  interface ProviderTextCollector { void capture(MeasuredText text); }
	
	Context2d context;
	int currentFontSize;
	Font currentFont= null;
	Color lastColor;
	static boolean isFullScreen=false;
	  private ProviderTextCollector providerTextCollector;
	
	  public Graphics(Context2d context) {
		    this.context = context;
		  }
	  
	  public void setColor(Color color) {
		    if (color != null) {
		      String colorString = color.getHexValue();
		      context.setStrokeStyle(colorString);
		      context.setFillStyle(colorString);
		    } else {
		      System.out.println("Ignoring null-Color");
		    }
		    lastColor=color;
		  }
	  
	  public void setColor(String color) {
	      context.setStrokeStyle(color);
	      context.setFillStyle(color);
	      lastColor=null;
	  }
	  
	  public void clipRect(int x, int y, int width, int height) {
		  context.beginPath();
		  context.rect(x, y, width, height);
		  context.clip();
	  }
	  
	  public void restore() {
		  context.restore();
	  }
	  
	  public void fillRect(int x, int y, int width, int height) {
		//  context.beginPath();
		  context.fillRect(x, y, width, height);
		//  context.closePath();
	  }
	  
	  public void drawRect(int x, int y, int width, int height) {
		//  context.beginPath();
		  context.strokeRect(x, y, width, height);
		//  context.closePath();
	  }
	  
	  public void fillOval(int x, int y, int width, int height) {
		  context.beginPath();
		  context.arc(x+width/2, y+width/2, width/2, 0, 2.0*3.14159);
		  context.closePath();
		  context.fill();
	  }
	  
	  public void drawString(String s, int x, int y){
		  context.fillText(s, x, y);
	  }

	  ProviderTextCollector swapProviderTextCollector(ProviderTextCollector next) {
	      ProviderTextCollector previous=providerTextCollector;
	      providerTextCollector=next;
	      return previous;
	  }

	  /** Only explicit installed-provider legends call this method. */
	  void drawProviderLabel(String id, String text, int ownerX, int ownerY, int x, int y) {
	      if (providerTextCollector == null) {
	          context.fillText(text, x, y);
	          return;
	      }
	      providerTextCollector.capture(measureText(id,text,ownerX,ownerY,x,y));
	  }

	  MeasuredText measureText(String id, String text, int ownerX, int ownerY, int x, int y) {
	      double[] m=new double[13];
	      String[] state=new String[4];
	      measureNative(context,text,m,state);
	      if (m[12] != 1) {
	          m[1]=0; m[2]=m[0];
	          m[3]=Math.max(1,currentFontSize*0.8); m[4]=Math.max(1,currentFontSize*0.2);
	      }
	      return new MeasuredText(id,text,ownerX,ownerY,x,y,currentFontSize,m,state);
	  }

	  void drawMeasuredText(MeasuredText text, int x, int y) {
	      if(!matchesTransform(text))
	          throw new IllegalStateException("PCB label canvas transform changed: "+text.id);
	      context.save();
	      try {
	          context.setFont(text.font);
	          context.setFillStyle(text.fillStyle);
	          context.setTextAlign(text.textAlign);
	          context.setTextBaseline(text.textBaseline);
	          context.setGlobalAlpha(text.alpha);
	          context.fillText(text.text,x,y);
	      } finally { context.restore(); }
	  }

	  private boolean matchesTransform(MeasuredText text) {
	      double[] m=new double[6];readTransformNative(context,m);
	      return Math.abs(m[0]-text.a)<0.000001&&Math.abs(m[1]-text.b)<0.000001&&
	          Math.abs(m[2]-text.c)<0.000001&&Math.abs(m[3]-text.d)<0.000001&&
	          Math.abs(m[4]-text.e)<0.000001&&Math.abs(m[5]-text.f)<0.000001;
	  }
	  private static native void readTransformNative(Context2d ctx,double[] m) /*-{
	      var t=(typeof ctx.getTransform==="function")?ctx.getTransform():null;
	      m[0]=t?t.a:1;m[1]=t?t.b:0;m[2]=t?t.c:0;
	      m[3]=t?t.d:1;m[4]=t?t.e:0;m[5]=t?t.f:0;
	  }-*/;

	  private static native void measureNative(Context2d ctx, String text,
	          double[] m, String[] state) /*-{
	      var tm=ctx.measureText(text);
	      m[0]=tm.width;
	      var actual=typeof tm.actualBoundingBoxLeft==="number" &&
	          typeof tm.actualBoundingBoxRight==="number" &&
	          typeof tm.actualBoundingBoxAscent==="number" &&
	          typeof tm.actualBoundingBoxDescent==="number";
	      if(actual) {
	          m[1]=tm.actualBoundingBoxLeft; m[2]=tm.actualBoundingBoxRight;
	          m[3]=tm.actualBoundingBoxAscent; m[4]=tm.actualBoundingBoxDescent;
	          m[12]=1;
	      } else { m[12]=0; }
	      var t=(typeof ctx.getTransform==="function") ? ctx.getTransform() : null;
	      m[5]=t?t.a:1; m[6]=t?t.b:0; m[7]=t?t.c:0;
	      m[8]=t?t.d:1; m[9]=t?t.e:0; m[10]=t?t.f:0;
	      m[11]=ctx.globalAlpha;
	      state[0]=ctx.font; state[1]=String(ctx.fillStyle);
	      state[2]=ctx.textAlign||"start"; state[3]=ctx.textBaseline||"alphabetic";
	  }-*/;
	  
	  public double measureWidth(String s) {
	      return context.measureText(s).getWidth();
	  }
	  
	  public void setLineWidth(double width){
		  context.setLineWidth(width);
	  }
	  
	  public void drawLine(int x1, int y1, int x2, int y2) {
		  context.beginPath();
		  context.moveTo(x1, y1);
		  context.lineTo(x2, y2);
		  context.stroke();
	//	  context.closePath();
	  }
	  
	  public void drawPolyline(int[] xpoints, int[] ypoints, int n) {
		  int i;
		  context.beginPath();
		  for (i=0; i<n;i++){
			  if (i==0)
				  context.moveTo(xpoints[i],ypoints[i]);
			  else
				  context.lineTo(xpoints[i],ypoints[i]);
		  }
		  context.closePath();
		  context.stroke();
	  }
	
	  
	  public void fillPolygon(Polygon p) {
		  int i;
		  context.beginPath();
		  for (i=0; i<p.npoints;i++){
			  if (i==0)
				  context.moveTo(p.xpoints[i],p.ypoints[i]);
			  else
				  context.lineTo(p.xpoints[i],p.ypoints[i]);
		  }
		  context.closePath();
		  context.fill();
		  }
	  
	  public void setFont(Font f){
		  if (f!=null){
			  context.setFont(f.fontname);
			  currentFontSize=f.size;
			  currentFont=f;
		  }
	  }
	  
	  Font getFont(){
		  return currentFont;
	  }
	  
	  public void drawLock(int x, int y) {
	      context.save();
	      setColor(new Color(209,75,75));
	      context.setLineWidth(3);
	      fillRect(x,y,30,20);
	      context.beginPath();
	      context.moveTo(x+15-10, y);
	      context.lineTo(x+15-10 , y -4);
	      context.arc(x+15,y-4,10,-3.1415, 0);
	      context.lineTo(x+15+10, y);
	      context.stroke();
	      context.restore();
	  }
	  
	   static int distanceSq(int x1, int y1, int x2, int y2) {
	    	x2 -= x1;
	    	y2 -= y1;
	    	return x2*x2+y2*y2;
	        }
	  
	   void setLineDash(int a, int b) {
	       setLineDash(context, a, b);
	   }
	   
	   native static void setLineDash(Context2d context, int a, int b) /*-{
	       if (a == 0)
	           context.setLineDash([]);
	       else
	       	   context.setLineDash([a, b]);
	   }-*/;
	   
	   
	   public static void viewFullScreen() {
	       requestFullScreen();
	       isFullScreen=true;
	   }
	   
	   private native static void requestFullScreen() /*-{
	   var element = $doc.documentElement;

	   if (element.requestFullscreen) {
	     element.requestFullscreen();
	   } else if (element.mozRequestFullScreen) {
	     element.mozRequestFullScreen();
	   } else if (element.webkitRequestFullscreen) {
	     element.webkitRequestFullscreen();
	   } else if (element.msRequestFullscreen) {
	     element.msRequestFullscreen();
	   }
	 }-*/;
	   
	   public static void exitFullScreen() {
	       requestExitFullScreen();
	       isFullScreen=false;
	   }
	   
	   private native static void requestExitFullScreen() /*-{
	   var d = $doc;

	   if (d.exitFullscreen) {
	     d.exitFullscreen();
	   } else if (d.mozExitFullScreen) {
	     d.mozExitFullScreen();
	   } else if (d.webkitExitFullscreen) {
	     d.webkitExitFullscreen();
	   } else if (d.msExitFullscreen) {
	     d.msExitFullscreen();
	   }
	 }-*/;
	   
	   
}
