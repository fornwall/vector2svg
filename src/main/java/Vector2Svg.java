/*
 * Copyright (C) 2015. Jared Rummler <me@jaredrummler.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Vector2Svg {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            printUsage();
            return;
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        System.out.printf("output=" + output.getAbsolutePath());
        if (!input.isFile()) {
            System.err.println("Input is not a file: " + input.getAbsolutePath());
            System.exit(1);
        } else if (!input.getName().endsWith(".xml")) {
            System.err.println("Input does not end with .xml: " + input.getAbsolutePath());
            System.exit(1);
        } else if (!output.getAbsoluteFile().getParentFile().isDirectory()) {
            System.err.println("Output directory does not exist: " + output.getAbsolutePath());
            System.exit(1);
        } else if (!output.getName().endsWith(".svg")) {
            System.err.println("Output file does not end with .svg: " + output.getAbsolutePath());
            System.exit(1);
        }

        createSvg(input, output);
    }

    private static void printUsage() {
        File jarFile =
                new File(Vector2Svg.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        System.out.println("Convert Android VectorDrawable XML resource file to SVG");
        System.out.println();
        System.out.println(String.format("Usage: java -jar %s [INPUT] [OUTPUT]", jarFile.getName()));
    }

    private static void createSvg(File input, File output) throws Exception {
        AndroidVectorDrawable drawable = getDrawable(input);
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element svg = doc.createElement("svg");
        svg.setAttribute("viewBox", "0 0 " + drawable.width + " " + drawable.height);
        svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
        svg.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        for (Group group : drawable.groups) {
            Element g = doc.createElement("g");
            for (VectorPath path : group.paths) {
                Element child = doc.createElement("path");
                if (path.fillColor != null) {
                    child.setAttribute("fill", path.fillColor);
                }
                if (path.strokeColor != null) {
                    child.setAttribute("stroke", path.strokeColor);
                }
                if (path.strokeWidth != null) {
                    child.setAttribute("stroke-width", path.strokeWidth);
                }
                child.setAttribute("d", path.pathData);
                g.appendChild(child);
            }
            svg.appendChild(g);
        }
        for (VectorPath path : drawable.paths) {
            Element child = doc.createElement("path");
            if (path.fillColor != null) {
                child.setAttribute("fill", path.fillColor);
            }
            if (path.strokeColor != null) {
                child.setAttribute("stroke", path.strokeColor);
            }
            if (path.strokeWidth != null) {
                child.setAttribute("stroke-width", path.strokeWidth);
            }
            child.setAttribute("d", path.pathData);
            svg.appendChild(child);
        }
        doc.appendChild(svg);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(source, result);
    }

    private static AndroidVectorDrawable getDrawable(File source)
            throws ParserConfigurationException, IOException, SAXException {
        Document xml =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source);
        xml.getDocumentElement().normalize();
        Node vector = xml.getElementsByTagName("vector").item(0);
        NamedNodeMap attributes = vector.getAttributes();
        NodeList children = vector.getChildNodes();

        int width = 0;
        int height = 0;
        for (int i = 0; i < attributes.getLength(); i++) {
            if (attributes.item(i).getNodeName().equals("android:viewportHeight")) {
                height = Integer.parseInt(attributes.item(i).getNodeValue());
            } else if (attributes.item(i).getNodeName().equals("android:viewportWidth")) {
                width = Integer.parseInt(attributes.item(i).getNodeValue());
            }
        }

        List<VectorPath> paths = new ArrayList<>();
        List<Group> groups = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node item = children.item(i);
            if (item.getNodeName().equals("group")) {
                List<VectorPath> groupPaths = new ArrayList<>();
                for (int j = 0; j < item.getChildNodes().getLength(); j++) {
                    VectorPath path = getVectorPathFromNode(item.getChildNodes().item(j));
                    if (path != null) {
                        groupPaths.add(path);
                    }
                }
                if (!groupPaths.isEmpty()) {
                    groups.add(new Group(groupPaths));
                }
            } else {
                VectorPath path = getVectorPathFromNode(item);
                if (path != null) {
                    paths.add(path);
                }
            }
        }

        return new AndroidVectorDrawable(paths, groups, width, height);
    }

    private static VectorPath getVectorPathFromNode(Node item) {
        if (item.getNodeName().equals("path")) {
            String pathData = null;
            String fillColor = null;
            String strokeColor = null;
            String strokeWidth = null;
            for (int j = 0; j < item.getAttributes().getLength(); j++) {
                Node node = item.getAttributes().item(j);
                String name = node.getNodeName();
                String value = node.getNodeValue();
                if (name.equals("android:pathData")) {
                    pathData = value;
                } else if (name.equals("android:fillColor") && value.startsWith("#")) {
                    fillColor = value;
                } else if (name.equals("android:strokeColor") && value.startsWith("#")) {
                    strokeColor = value;
                } else if (name.equals("android:strokeWidth")) {
                    strokeWidth = value;
                }
            }
            if (pathData != null) {
                return new VectorPath(pathData, fillColor, strokeColor, strokeWidth);
            }
        }
        return null;
    }

    private static class VectorPath {
        private String pathData;
        private String fillColor;
        private String strokeColor;
        private String strokeWidth;

        private VectorPath(String pathData, String fillColor, String strokeColor, String strokeWidth) {
            this.pathData = pathData;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
        }
    }

    private static class Group {
        private final List<VectorPath> paths;

        public Group(List<VectorPath> paths) {
            this.paths = paths;
        }
    }

    private static class AndroidVectorDrawable {
        private final List<VectorPath> paths;
        private final List<Group> groups;
        private final int height;
        private final int width;

        private AndroidVectorDrawable(List<VectorPath> paths, List<Group> groups, int width, int height) {
            this.paths = paths;
            this.groups = groups;
            this.height = height;
            this.width = width;
        }
    }

}
