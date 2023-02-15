# EC-BigIp

This plugin allows to work with F5 Big-IP via iControlREST API.


# Procedures

## Common operations

### LTM - Config sync

Synchronizes the local BIG-IP device to the device group

## Balancing pool operations

### LTM - Create or update balancing pool

Create or update balancing pool configuration

### LTM - Get pool list

Get balancing pool list

### LTM - Get balancing pool

Get pool configuration

### LTM - Delete balancing pool

Delete balancing pool configuration


## Pool member operations

### LTM - Create or update pool member

Create or update the set of pool members that are associated with a load balancing pool

### LTM - Change pool member status

Change pool member status

### LTM - Get member list

Get all pool members that make up a load balancing pool

### LTM - Get pool member

Get a specified pool member from a load balancing pool

### LTM - Delete pool member

Delete a specified pool member from a load balancing pool

# Building the plugin

1. Download or clone the EC-BigIp repository.

    ```
    git clone https://github.com/electric-cloud/EC-BigIp.git
    ```

5. Zip up the files to create the plugin zip file.

    ```
     cd EC-BigIp
     zip -r EC-BigIp.zip ./*
    ```

6. Import the plugin zip file into your {CD} server and promote it.
