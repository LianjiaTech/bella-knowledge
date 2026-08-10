package com.ke.bella.files.enums;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ke.bella.files.db.tables.pojos.FileDB;

public class NodeTypeTest {

    @Test
    public void directoryFlagWinsOverDefaultNodeTypeDuringRollingDeployment() {
        FileDB file = new FileDB();
        file.setIsDir(1);
        file.setNodeType(NodeType.FILE.getValue());

        assertEquals(NodeType.DIRECTORY, NodeType.from(file));
    }

    @Test
    public void resourceRemainsResourceForLeafNodes() {
        FileDB file = new FileDB();
        file.setIsDir(0);
        file.setNodeType(NodeType.RESOURCE.getValue());

        assertEquals(NodeType.RESOURCE, NodeType.from(file));
    }
}
