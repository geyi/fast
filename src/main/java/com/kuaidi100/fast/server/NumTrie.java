package com.kuaidi100.fast.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class NumTrie {

    private Node root = new Node();

    public NumTrie() {
    }

    public Node getRoot() {
        return root;
    }

    public void add(String numPrefix) {
        Node parent = root;
        for (int i = 0, limit = numPrefix.length(); i < limit; i++) {
            char n = numPrefix.charAt(i);
            Byte num = Byte.valueOf(String.valueOf(n));
            /* 基于数组
            Node node = new Node(num, i);
            int idx = parent.getNodes().indexOf(node);
            if (idx >= 0) {
                parent = parent.getNodes().get(idx);
                continue;
            } else {
                parent.setNodes(node);
                parent = node;
            }*/
            /* 基于map */
            Node node = parent.getChildren().get(num);
            if (node == null) {
                node = new Node(num, i);
                parent.getChildren().put(num, node);
                parent = node;
            } else {
                parent = node;
                continue;
            }
        }
    }

    public boolean search(String str) {
        Node parent = root;
        int count = 0;
        int jumpCount = 0;
        /* 基于数组
        for (int i = 0, limit = str.length(); i < limit; i++) {
            if (count >= 99) {
                break;
            }
            char n = str.charAt(i);
            Byte num = Byte.parseByte(String.valueOf(n));
            Node node = new Node(num);
            int idx = parent.getNodes().indexOf(node);
            if (idx == -1) {
                if (jumpCount < 9 && i > 1) {
                    n = str.charAt(++i);
                    num = Byte.parseByte(String.valueOf(n));
                    node = new Node(num);
                    idx = parent.getNodes().indexOf(node);
                    if (idx == -1) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            parent = parent.getNodes().get(idx);
            count++;
        }*/
        for (int i = 0, limit = str.length(); i < limit; i++) {
            if (count >= 79) {
                break;
            }
            char n = str.charAt(i);
            Byte num = Byte.valueOf(String.valueOf(n));
            Node node = parent.getChildren().get(num);
            if (node == null) {
                if (jumpCount < 9 && i > 1) {
                    jumpCount++;
                    n = str.charAt(++i);
                    num = Byte.valueOf(String.valueOf(n));
                    node = parent.getChildren().get(num);
                    if (node == null) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            parent = node;
            count++;
        }
        return true;
    }

    /*
    root, 0, numIndex
     */
    public List<Integer> fullSearch(Node parent, int offset, NumIndex numIndex) {
        List<Integer> offsetList = new ArrayList<>();
        for (NumTrie.Node node : parent.getNodes()) {
            byte num = node.getValue();
//            long[] bitmap = numIndex.getPositionMap().get(num);
            int tmp = offset;
            while ((tmp = numIndex.nextBitIndex(num, tmp)) != -1) {
                // 如果是最后一层
                if (node.getLevel() == 9) {
                    offsetList.add(tmp);
                    continue;
                }
                fullSearch(node, tmp++, numIndex);

            }
        }

        return null;
    }

    /*
    root, 0, 31, numIndex
     */
    public void test(Node parent, int idx, int offset, NumIndex numIndex, List<Integer> ret, int os) {
        if (parent.getNodes().isEmpty()) {
            // 返回匹配上的偏移
            ret.add(os);
        }
        // 判断32位是否有
        NumTrie.Node numNode = parent.getNodes().get(idx);
        for (int i = 0, limit = numNode.getNodes().size(); i < limit; i++) {
            Node nn = numNode.getNodes().get(i);
            byte value = nn.getValue();
            int tmpOffset;
            if (numIndex.get(value, tmpOffset = offset + 1) || numIndex.get(value, tmpOffset = offset + 2)) {
                test(numNode, i, tmpOffset, numIndex, ret, os);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        NumTrie numTrie = new NumTrie();

        String path = "C:\\Users\\kuaidi100\\Desktop\\test_data_10000.txt";
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        int totalTime = 0;
        while ((line = reader.readLine()) != null) {
            String[] arr = line.split(",");
            numTrie.add(arr[4]);
        }
        System.out.println();
        System.out.println(numTrie.search("5985939384448459957124564476648221980420899639437151929221315950315378552187188532797434599502393889"));

    }

    static class Node {
        byte value;
        List<Node> nodes = new ArrayList<>(10);
        Map<Byte, Node> children = new HashMap<>();
        int level;

        public Node() {
        }

        public Node(byte value) {
            this.value = value;
        }

        public Node(byte value, int level) {
            this.value = value;
            this.level = level;
        }

        public byte getValue() {
            return value;
        }

        public void setValue(byte value) {
            this.value = value;
        }

        public List<Node> getNodes() {
            return nodes;
        }

        public void setNodes(Node node) {
            this.nodes.add(node);
        }

        public Map<Byte, Node> getChildren() {
            return children;
        }

        public void setChildren(Map<Byte, Node> children) {
            this.children = children;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return value == node.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
