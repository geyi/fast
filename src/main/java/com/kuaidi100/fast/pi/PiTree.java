package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Constant;
import com.kuaidi100.fast.server.NumIndex;
import com.kuaidi100.fast.server.PiUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PiTree {
    private final static Node ROOT_NODE = new Node((byte) -1);
    private Node root = ROOT_NODE;
    private int maxLevel;
    private NumIndex numIndex;

    public PiTree(int maxLevel) {
        this.maxLevel = maxLevel;
        this.numIndex = new NumIndex(Constant.BASE_PATH + "pi-200m.txt");
    }

    public Pattern addPattern(String pattern) {
        Pattern p = new Pattern() {
            @Override
            public String getPattern() {
                return pattern;
            }
        };
        addPattern(p);
        return p;
    }

    public void addPattern(Pattern pattern) {
        byte[] bytes = pattern.getPattern().getBytes();
        if (bytes.length % maxLevel != 0) {
            throw new IllegalArgumentException("byte array length must be 100 but " + bytes.length);
        }
        byte prefix = getPrefix(bytes, 0);
        for (int i = 0; i < bytes.length / maxLevel; i++) {
            int start = maxLevel * i;
            int end = maxLevel * (i + 1) - 1;
            Node node = root;
            for (int j = start; j <= end; j++) {
                byte b = bytes[j];
                int idx = b - 48;
                if (node.children == null) {
                    node.children = new Node[10];
                }
                if (node.children[idx] == null) {
                    node.children[idx] = new Node(b);
                }
                node = node.children[idx];
            }
            if (node.parts == null) {
                node.parts = new HashSet<>();
            }
            node.parts.add(new MatchPart(pattern, prefix, (byte) i));
        }
    }

    public Map<Pattern, Map<Integer, Integer>> matches(String source) {
        byte[] bytes = source.getBytes();
        return matches(bytes, 0, bytes.length);
    }

    public Map<Pattern, Map<Integer, Integer>> matches(byte[] bytes, int pos, int length) {
        int end = pos + length;
        end = end < bytes.length - maxLevel ? end : bytes.length - maxLevel;
        Map<Pattern, Map<Integer, Integer>> result = new HashMap<>();
        for (int i = pos; i <= end; i++) {
            Set<MatchPart> parts = match(bytes, i, maxLevel);
            if (parts != null) {
                for (MatchPart part : parts) {
                    fillPatternMap(part, i, bytes, pos, result);
                }
            }
        }
        return result;
    }

    private void fillPatternMap(MatchPart part,
                                int index,
                                byte[] bytes,
                                int startPos,
                                Map<Pattern, Map<Integer, Integer>> result) {
        index = getPartIndex(part, index, bytes, startPos, numIndex);
        if (index == -1) {
            return;
        }
        Map<Integer, Integer> tmp = result.get(part.pattern);
        if (tmp == null) {
            tmp = new HashMap<>();
            result.put(part.pattern, tmp);
        }
        Integer count = tmp.get(index);
        if (count == null) {
            tmp.put(index, 1);
        } else {
            tmp.put(index, ++count);
        }
    }

    public Map<Pattern, Integer> getPatternIndex(Map<Pattern, Map<Integer, Integer>> map, byte[] bytes) {
        Map<Pattern, Integer> result = new HashMap<>();
        for (Pattern pattern : map.keySet()) {
            Map<Integer, Integer> count = map.get(pattern);
            if (count == null) {
                result.put(pattern, -1);
            } else {
                int max = 0;
                for (Integer idx : count.keySet()) {
                    if (count.get(idx) > max) {
                        result.put(pattern, idx);
                    }
                    max = count.get(idx);
                }
            }
        }
//        for (Pattern key : result.keySet()) {
//            int index = result.get(key);
//            if (index != -1) {
//                JudgeTree tree = new JudgeTree();
//                tree.addPattern(key.getPattern().getBytes());
//                index = tree.matches(bytes, index - 10, 119);
//                result.put(key, index);
//            }
//        }
        return result;
    }

    private int getPartIndex(MatchPart part, int index, byte[] bytes, int pos) {
        if (part.partIndex == 0) {
            return index;
        }
        index = index - (maxLevel * ((int) part.partIndex) + 1) - 1;
        int result = judgeIndex(part, index, bytes, pos);
        if (result == -1) {
            result = judgeIndex(part, index - maxLevel, bytes, pos);
        }
        return result;
    }

    private int getPartIndex(MatchPart part, int index, byte[] bytes, int pos, NumIndex numIndex) {
        if (part.partIndex == 0) {
            return index;
        }
        index = index - (maxLevel * ((int) part.partIndex) + 1) - 1;
        int result = judgeIndex(part, index, bytes, pos);
        if (result == -1) {
            result = numIndex.getOffsetFrom(part.pattern.getPattern(), index - maxLevel);
        }
        return result;
    }

    private int judgeIndex(MatchPart part, int index, byte[] bytes, int pos) {
        index = index < pos ? pos : index;
        for (int i = 0; i < maxLevel; i++) {
            int idx = index + i;
            byte prefix = getPrefix(bytes[idx - 1], bytes[idx]);
            if (prefix == part.prefix) {
                return idx - 1;
            }
        }
        return -1;
    }

    /**
     * 匹配10个数字的某一段
     *
     * @param bytes
     * @param pos
     * @param length
     * @return
     */
    private Set<MatchPart> match(byte[] bytes, int pos, int length) {
        Node node = root;
        int i = pos;
        int end = pos + length;
        end = end < bytes.length ? end : bytes.length - 1;
        while (node.children != null && i < end) {
            byte b = bytes[i];
            int idx = b - 48;
            if (node.children[idx] == null) {
                break;
            }
            node = node.children[idx];
            i++;
        }
        if (node.parts != null) {
            return node.parts;
        }
        return null;
    }

    private byte getPrefix(byte first, byte second) {
        return (byte) Integer.parseInt((first - 48) + "" + (second - 48));
    }

    private byte getPrefix(byte[] bytes, int pos) {
        return getPrefix(bytes[pos], bytes[pos + 1]);
    }

    private static class MatchPart {
        private Pattern pattern;
        private byte prefix;
        private byte partIndex;

        private MatchPart(Pattern pattern, byte prefix, byte partIndex) {
            this.pattern = pattern;
            this.prefix = prefix;
            this.partIndex = partIndex;
        }

        @Override
        public int hashCode() {
            return pattern.hashCode() + prefix;
        }

        @Override
        public boolean equals(Object obj) {
            return hashCode() == obj.hashCode();
        }

        @Override
        public String toString() {
            return "MatchPart{" +
                    "pattern=" + pattern +
                    ", prefix=" + prefix +
                    ", partIndex=" + partIndex +
                    '}';
        }
    }

    public static abstract class Pattern {
        public abstract String getPattern();

        @Override
        public String toString() {
            return getPattern();
        }

        @Override
        public int hashCode() {
            return getPattern() == null ? -1 : getPattern().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || (!(obj instanceof Pattern))) {
                return false;
            }
            return hashCode() == obj.hashCode();
        }
    }

    private static class Node {
        private byte value;
        private Node[] children;
        private Set<MatchPart> parts;

        private Node(byte value) {
            this.value = value;
        }
    }

    private static class JudgeTree {
        private Node root = new Node((byte) -1);

        private void addPattern(byte[] patterns) {
            Node node = root;
            for (int i = 0; i < patterns.length; i++) {
                byte b = patterns[i];
                int idx = b - 48;
                if (node.children == null) {
                    node.children = new Node[10];
                }
                if (node.children[idx] == null) {
                    node.children[idx] = new Node(b);
                }
                node = node.children[idx];
            }
        }

        private int matches(byte[] bytes, int pos, int length) {
            int end = pos + length;
            end = end < bytes.length ? end : bytes.length - 1;
            for (int i = 0; i < 10; i++) {
                Node node = root;
                int missMatch = 0;
                int p = pos;
                boolean done = true;
                while (node != null && node.children != null && p < end) {
                    byte b = bytes[p];
                    int idx = b - 48;
                    if (node.children[idx] == null) {
                        if (p - pos < 2) {
                            done = false;
                            break;
                        }
                        missMatch++;
                        if (missMatch > 9) {
                            done = false;
                            break;
                        }
                    } else {
                        node = node.children[idx];
                    }
                    p++;
                }
                if (done) {
                    break;
                }
                pos++;
            }
            return pos;
        }
    }

    public static void main(String[] args) throws IOException {


        String pattern = "1160902760295994162651784447730911185638855599865372816221178941988614580870332961023372299751257621";
        String pi = PiUtils.readPi(Constant.BASE_PATH + "pi-200m.txt");
        PiTree tree = new PiTree(10);
        tree.addPattern(pattern);
        Map<Pattern, Integer> result = tree.getPatternIndex(tree.matches(pi), pi.getBytes());
        for (Pattern p : result.keySet()) {
            System.out.println(p.getPattern() + " " + result.get(p));
        }
    }
}
