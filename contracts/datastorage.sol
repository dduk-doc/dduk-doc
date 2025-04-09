// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract DocumentRegistry {

    struct Document {
        string name;
        string uri;
        string hash;
        uint256 timestamp;
        address owner;
    }

    mapping(string => Document) private documents;
    string[] private documentNames;

    event DocumentRegistered(string name, string hash, string uri);
    event DocumentDeleted(string name);

    function registerDocument(string memory name, string memory hash, string memory uri) public {
        require(bytes(documents[name].name).length == 0, "Document already exists");

        documents[name] = Document({
            name: name,
            uri: uri,
            hash: hash,
            timestamp: block.timestamp,
            owner: msg.sender
        });

        documentNames.push(name);

        emit DocumentRegistered(name, hash, uri);
    }

    function getDocument(string memory name) public view returns (string memory, string memory, uint256) {
        Document memory doc = documents[name];
        require(bytes(doc.name).length > 0, "Document not found");

        return (doc.uri, doc.hash, doc.timestamp);
    }

    function getAllDocuments() public view returns (Document[] memory) {
        Document[] memory result = new Document[](documentNames.length);

        for (uint i = 0; i < documentNames.length; i++) {
            result[i] = documents[documentNames[i]];
        }

        return result;
    }

    function deleteDocument(string memory name) public {
        Document memory doc = documents[name];
        require(doc.owner == msg.sender, "Only owner can delete");

        delete documents[name];

        // remove from documentNames
        for (uint i = 0; i < documentNames.length; i++) {
            if (keccak256(abi.encodePacked(documentNames[i])) == keccak256(abi.encodePacked(name))) {
                documentNames[i] = documentNames[documentNames.length - 1];
                documentNames.pop();
                break;
            }
        }

        emit DocumentDeleted(name);
    }
}
