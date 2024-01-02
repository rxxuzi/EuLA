package global;

import com.google.gson.Gson;
import com.jcraft.jsch.*;

import java.io.*;

import static global.Config.*;

public final class SSH implements AutoCloseable{
    public final String host;
    public final int port;
    public final String user;
    public final String password;
    public final String userHost;

    private Session session;

    public SSH() {
        this(SSH_PATH);
    }

    public SSH(String filePath) {
        SSHConfigData configData = loadConfig(filePath);
        this.host = configData.host;
        this.port = configData.port;
        this.user = configData.user;
        this.password = configData.password;
        this.userHost = this.user + "@" + this.host;
    }

    public SSH(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.userHost = this.user + "@" + this.host;
    }

    private SSHConfigData loadConfig(String filePath) {
        Gson gson = new Gson();
        try (Reader reader = new FileReader(filePath)) {
            return gson.fromJson(reader, SSHConfigData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SSHConfigData {
        String host;
        int port;
        String user;
        String password;
    }

    public void open() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(this.user, this.host, this.port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword(this.password);
        session.connect();
    }

    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public void send(String localPath, String remotePath) throws JSchException, SftpException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH session not connected.");
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.put(localPath, remotePath);
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.exit();
                sftpChannel.disconnect();
            }
        }
    }

    public void send(File localFile, String remotePath) throws JSchException, SftpException, IOException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH session not connected.");
        }
        ChannelSftp sftpChannel = null;
        try (FileInputStream fis = new FileInputStream(localFile)){
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.put(fis, remotePath);
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.exit();
                sftpChannel.disconnect();
            }
        }
    }

    public void get(String remotePath, String localPath) throws JSchException, SftpException, IOException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH session not connected.");
        }
        ChannelSftp sftpChannel = null;
        try {
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            sftpChannel.get(remotePath, localPath);
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.exit();
                sftpChannel.disconnect();
            }
        }
    }


    public String exec(String command) throws JSchException, IOException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("SSH session not connected.");
        }
        String output;
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);
            try (InputStream in = channel.getInputStream()) {
                channel.connect();
                output = new String(in.readAllBytes());
            }
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return output;
    }

    public String output(Channel channel) throws IOException, JSchException {
        InputStream input = channel.getInputStream();
        channel.connect();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString().trim();
    }
}

