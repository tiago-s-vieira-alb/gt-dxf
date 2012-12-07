/*
 * [ 1719398 ] First shot at LWPOLYLINE
 * Peter Hopfgartner - hopfgartner
 *  
 */
package org.geotools.data.dxf.entities;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LinearRing;
import org.geotools.data.dxf.parser.DXFLineNumberReader;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.geotools.data.GeometryType;
import org.geotools.data.dxf.parser.DXFUnivers;
import org.geotools.data.dxf.header.DXFLayer;
import org.geotools.data.dxf.header.DXFLineType;
import org.geotools.data.dxf.header.DXFTables;
import org.geotools.data.dxf.parser.DXFCodeValuePair;
import org.geotools.data.dxf.parser.DXFGroupCode;
import org.geotools.data.dxf.parser.DXFParseException;

public class DXFLwPolyline extends DXFEntity {
    public String _id = "DXFLwPolyline";
    public int _flag = 0;
    public ArrayList<DXFLwVertex> theVertices = new ArrayList<DXFLwVertex>();

    public DXFLwPolyline(String name, int flag, int c, DXFLayer l, List<DXFLwVertex> v, int visibility, DXFLineType lineType, double thickness) {
        super(c, l, 0, lineType, thickness);
        _id = name;


        ArrayList<DXFLwVertex> newV = new ArrayList<DXFLwVertex>();

        for (DXFLwVertex vertex : v)
            newV.add((DXFLwVertex)vertex.clone());

        theVertices = newV;
        _flag = flag;
    }

    public DXFLwPolyline(DXFLwPolyline orig) {
        super(orig.getColor(), orig.getRefLayer(), 0, orig.getLineType(), orig.getThickness());
        _id = orig._id;

        for (DXFLwVertex vertex : orig.theVertices)
            theVertices.add((DXFLwVertex)vertex.clone());

        _flag = orig._flag;

        setType(orig.getType());
        setUnivers(orig.getUnivers());
    }

    public static DXFLwPolyline read(DXFLineNumberReader br, DXFUnivers univers) throws IOException {
        String name = "";
        int visibility = 0, flag = 0, c = -1;
        DXFLineType lineType = null;
        ArrayList<DXFLwVertex> lv = new ArrayList<DXFLwVertex>();
        DXFLayer l = null;

        DXFCodeValuePair cvp = null;
        DXFGroupCode gc = null;

        boolean doLoop = true;
        while (doLoop) {
            cvp = new DXFCodeValuePair();
            try {
                gc = cvp.read(br);
            } catch (DXFParseException ex) {
                throw new IOException("DXF parse error" + ex.getLocalizedMessage());
            } catch (EOFException e) {
                doLoop = false;
                break;
            }

            switch (gc) {
                case TYPE:
                    String type = cvp.getStringValue(); // SEQEND ???
                    // geldt voor alle waarden van type
                    br.reset();
                    doLoop = false;
                    break;
                case X_1: //"10"
                    br.reset();
                    readLwVertices(br, lv);
                    break;
                case NAME: //"2"
                    name = cvp.getStringValue();
                    break;
                case LAYER_NAME: //"8"
                    l = univers.findLayer(cvp.getStringValue());
                    break;
                case LINETYPE_NAME: //"6"
                    lineType = univers.findLType(cvp.getStringValue());
                    break;
                case COLOR: //"62"
                    c = cvp.getShortValue();
                    break;
                case INT_1: //"70"
                    flag = cvp.getShortValue();
                    break;
                case VISIBILITY: //"60"
                    visibility = cvp.getShortValue();
                    break;
                default:
                    break;
            }

        }
        DXFLwPolyline e = new DXFLwPolyline(name, flag, c, l, lv, visibility, lineType, DXFTables.defaultThickness);
        if ((flag & 1) == 1) {
            e.setType(GeometryType.POLYGON);
        } else {
            e.setType(GeometryType.LINE);
        }
        e.setUnivers(univers);
        return e;
    }

    public static void readLwVertices(DXFLineNumberReader br, List<DXFLwVertex> theVertices) throws IOException {
        double x = 0, y = 0, b = 0;
        boolean xFound = false, yFound = false;

        DXFCodeValuePair cvp = null;
        DXFGroupCode gc = null;

        boolean doLoop = true;
        while (doLoop) {
            cvp = new DXFCodeValuePair();
            try {
                gc = cvp.read(br);
            } catch (DXFParseException ex) {
                throw new IOException("DXF parse error" + ex.getLocalizedMessage());
            } catch (EOFException e) {
                doLoop = false;
                break;
            }

            switch (gc) {
                case TYPE:
                case X_1: //"10"
                    // check of vorig vertex opgeslagen kan worden
                    if (xFound && yFound) {
                        DXFLwVertex e = new DXFLwVertex(x, y, b);
                        theVertices.add(e);
                        xFound = false;
                        yFound = false;
                        x = 0;
                        y = 0;
                        b = 0;
                    }
                    // TODO klopt dit???
                    if (gc == DXFGroupCode.TYPE) {
                        br.reset();
                        doLoop = false;
                        break;
                    }
                    x = cvp.getDoubleValue();
                    xFound = true;
                    break;
                case Y_1: //"20"
                    y = cvp.getDoubleValue();
                    yFound = true;
                    break;
                case DOUBLE_3: //"42"
                    b = cvp.getDoubleValue();
                    break;
                default:
                    break;
            }

        }
    }

    @Override
    public Geometry getGeometry() {
        if (geometry == null) {
            updateGeometry();
        }
        return super.getGeometry();
    }

    @Override
    public void updateGeometry() {
        Coordinate[] ca = toCoordinateArray();

        if (ca != null && ca.length >= 2) {
            if (getType() == GeometryType.POLYGON && ca.length >= 3) {
                LinearRing lr = getUnivers().getGeometryFactory().createLinearRing(ca);
                geometry = getUnivers().getGeometryFactory().createPolygon(lr, null);
            } else {
                geometry = getUnivers().getGeometryFactory().createLineString(ca);
            }
        } else {
        }
    }

    public Coordinate[] toCoordinateArray() {
        if (theVertices == null) {
            return null;
        }


        Iterator it = theVertices.iterator();
        List<Coordinate> lc = new ArrayList<Coordinate>();
        while (it.hasNext()) {
            DXFLwVertex v = (DXFLwVertex) it.next();
            lc.add(v.toCoordinate());
        }

        if (lc.size() == 2) {
            setType(GeometryType.LINE);
        }

        if (getType() == GeometryType.POLYGON) {
            if (lc.size() >= 3) {
                Coordinate firstc = lc.get(0);
                Coordinate lastc = lc.get(lc.size() - 1);
                if (!firstc.equals2D(lastc)) {
                    lc.add(firstc);
                }
            }
        }

        /* TODO uitzoeken of lijn zichzelf snijdt, zo ja nodding
         * zie jts union:
         * Collection lineStrings = . . .
         * Geometry nodedLineStrings = (LineString) lineStrings.get(0);
         * for (int i = 1; i < lineStrings.size(); i++) {
         * nodedLineStrings = nodedLineStrings.union((LineString)lineStrings.get(i));
         * */
        return rotateAndPlace(lc.toArray(new Coordinate[]{}));
    }

    public String toString(String name, int flag, int numVert, int c, int visibility, double thickness) {
        StringBuilder s = new StringBuilder();
        s.append("DXFPolyline [");
        s.append("name: ");
        s.append(name + ", ");
        s.append("flag: ");
        s.append(flag + ", ");
        s.append("numVert: ");
        s.append(numVert + ", ");
        s.append("color: ");
        s.append(c + ", ");
        s.append("visibility: ");
        s.append(visibility + ", ");
        s.append("thickness: ");
        s.append(thickness);
        s.append("]");
        return s.toString();
    }

    @Override
    public String toString() {
        return toString("DXFLwPolyline", _flag, theVertices.size(), getColor(), (isVisible() ? 0 : 1), getThickness());
    }

    @Override
    public DXFEntity translate(double x, double y) {
        // Move all vertices
        Iterator iter = theVertices.iterator();
        while (iter.hasNext()) {
            DXFLwVertex vertex = (DXFLwVertex) iter.next();
            vertex._point.x += x;
            vertex._point.y += y;
        }
        return this;
    }

    @Override
    public DXFEntity clone() {
        return new DXFLwPolyline(this);
    }
}

